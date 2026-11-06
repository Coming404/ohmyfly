// 后台单写者：事件和控制操作共用有界队列，clear 的顺序由同一队列确定。
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.ohmyfly.logging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okio.FileSystem
import okio.SYSTEM
import okio.Path
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Clock

internal class LogWorker(
    private val scope: CoroutineScope,
    private val fs: FileSystem = FileSystem.SYSTEM,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private sealed interface Command
    private data class Event(val entry: LogEntry) : Command
    private class Request(
        val action: Action,
        val policy: LogPolicy? = null,
        val level: LogLevel = LogLevel.Detail,
        val cursor: LogCursor? = null,
        val result: CompletableDeferred<LogPage?> = CompletableDeferred(),
    ) : Command
    private enum class Action { Configure, Page, Clear, Flush }
    private val commands = Channel<Command>(QUEUE_CAPACITY)
    private val started = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(LogStorageState())
    val state: StateFlow<LogStorageState> = mutableState.asStateFlow()
    private val pending = ArrayList<LogEntry>(QUEUE_CAPACITY)
    private var store: FileLogStore? = null
    private var configured = false
    private var nextFlush = 0L

    fun start(directory: () -> Path): Boolean {
        if (!started.compareAndSet(false, true)) return false
        scope.launch { run(directory) }
        return true
    }

    fun enqueue(entry: LogEntry) {
        if (!commands.trySend(Event(entry)).isSuccess) dropped(1)
    }

    suspend fun configure(policy: LogPolicy) {
        policy.validate()
        request(Request(Action.Configure, policy = policy))
    }

    suspend fun page(level: LogLevel, cursor: LogCursor?): LogPage =
        request(Request(Action.Page, level = level, cursor = cursor))!!

    suspend fun clear() { request(Request(Action.Clear)) }
    suspend fun flush() { request(Request(Action.Flush)) }

    private suspend fun request(command: Request): LogPage? {
        commands.send(command)
        return command.result.await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun run(directory: () -> Path) {
        while (currentCoroutineContext().isActive) {
            val command = select<Command?> {
                commands.onReceive { it }
                onTimeout(250) { null }
            }
            when (command) {
                is Event -> {
                    if (pending.size == QUEUE_CAPACITY) dropped(1) else pending += command.entry
                    if (configured && (pending.size >= 32 || now() >= nextFlush)) {
                        flushPending()
                        nextFlush = now() + 250
                        maintain()
                    }
                }
                is Request -> handle(command, directory)
                null -> {
                    flushPending()
                    if (configured) maintain()
                }
            }
        }
    }

    private fun handle(command: Request, directory: () -> Path) {
        try {
            var page: LogPage? = null
            when (command.action) {
                Action.Configure -> {
                    flushPending()
                    val target = store ?: FileLogStore(fs, directory(), now).also { store = it }
                    target.configure(command.policy!!)
                    configured = true
                    publish(failure = null)
                    flushPending()
                }
                Action.Page -> {
                    flushPending()
                    page = readyStore().page(command.level, command.cursor)
                    publish()
                }
                Action.Clear -> {
                    pending.clear()
                    readyStore().clear()
                    publish(failure = null)
                }
                Action.Flush -> flushPending()
            }
            command.result.complete(page)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val message = if (error is LogStorageException) error.message!! else "日志存储操作失败，请检查可用空间后重试"
            publish(failure = message)
            command.result.completeExceptionally(LogStorageException(message))
        }
    }

    private fun readyStore(): FileLogStore {
        if (!configured) throw LogStorageException("日志尚未完成配置")
        return store!!
    }

    private fun flushPending() {
        if (!configured || pending.isEmpty()) return
        try {
            store!!.append(pending)
            publish(failure = null)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            store!!.abandonWriter()
            dropped(pending.size.toLong())
            publish(failure = "日志写入失败，部分记录可能未保存")
        } finally {
            pending.clear()
        }
    }

    private fun maintain() {
        try {
            store!!.maintainIfDue()
            publish()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            publish(failure = "日志保留清理失败，请重试")
        }
    }

    private fun dropped(count: Long) {
        mutableState.update { it.copy(dropped = it.dropped + count) }
    }

    private fun publish(failure: String? = mutableState.value.failure) {
        mutableState.update {
            it.copy(bytes = store?.bytes ?: 0, files = store?.fileCount ?: 0, failure = failure, ready = configured)
        }
    }
}
