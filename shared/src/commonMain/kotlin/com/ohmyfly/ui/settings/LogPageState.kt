// 页面只保留当前页记录；历史只保存最多32个轻量游标，不累积日志正文。
package com.ohmyfly.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ohmyfly.logging.AppLog
import com.ohmyfly.logging.LogCursor
import com.ohmyfly.logging.LogEntry
import com.ohmyfly.logging.LogLevel
import com.ohmyfly.logging.LogPage
import kotlinx.coroutines.CancellationException

internal class LogPageState(
    private val read: suspend (LogLevel, LogCursor?) -> LogPage = AppLog::page,
    private val erase: suspend () -> Unit = AppLog::clear,
) {
    var level by mutableStateOf(LogLevel.Detail)
        private set
    var entries by mutableStateOf(emptyList<LogEntry>())
        private set
    var busy by mutableStateOf(false)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var next by mutableStateOf<LogCursor?>(null)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    private var current: LogCursor? = null
    private val previous = ArrayDeque<LogCursor?>()

    suspend fun refresh(selected: LogLevel = level) {
        if (busy) return
        level = selected
        previous.clear()
        canGoBack = false
        entries = emptyList()
        next = null
        current = null
        load(null)
    }

    suspend fun older() {
        if (busy) return
        val cursor = next ?: return
        val before = current
        if (load(cursor)) {
            if (previous.size == 32) previous.removeFirst()
            previous.addLast(before)
            canGoBack = true
        }
    }

    suspend fun newer() {
        if (busy || previous.isEmpty()) return
        if (load(previous.last())) {
            previous.removeLast()
            canGoBack = previous.isNotEmpty()
        }
    }

    suspend fun clear(): Boolean {
        if (busy) return false
        busy = true
        failure = null
        return try {
            erase()
            entries = emptyList()
            next = null
            current = null
            previous.clear()
            canGoBack = false
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failure = "日志清理失败，请重试"
            false
        } finally {
            busy = false
        }
    }

    private suspend fun load(cursor: LogCursor?): Boolean {
        busy = true
        failure = null
        return try {
            val page = read(level, cursor)
            if (page.invalidated) {
                entries = emptyList()
                next = null
                previous.clear()
                canGoBack = false
                current = null
                failure = "日志文件已轮转或清理，请刷新"
                false
            } else {
                entries = page.entries
                next = page.next
                current = cursor
                true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failure = "日志读取失败，请重试"
            false
        } finally {
            busy = false
        }
    }
}
