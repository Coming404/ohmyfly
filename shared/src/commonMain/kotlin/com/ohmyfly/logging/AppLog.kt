// 统一安装文件 Appender；业务直接使用 kotlin-logging 的 info/debug 调用。
package com.ohmyfly.logging

import io.github.oshai.kotlinlogging.Appender
import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okio.Path

internal val logger get() = AppLog.logger

internal object AppLog {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val worker = LogWorker(scope)
    val state: StateFlow<LogStorageState> = worker.state
    val logger = run {
        KotlinLoggingConfiguration.logStartupMessage = false
        KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
        KotlinLoggingConfiguration.direct.logLevel = Level.DEBUG
        KotlinLoggingConfiguration.direct.appender = object : Appender {
            override fun log(loggingEvent: KLoggingEvent) {
                if (loggingEvent.level == Level.OFF) return
                // 不保存 Throwable 或任意 payload；调用点负责只提供安全、明确的文案。
                val text = loggingEvent.message.orEmpty()
                worker.enqueue(LogEntry(
                    id = newLogId(), timestampMillis = loggingEvent.timestamp,
                    level = if (loggingEvent.level == Level.DEBUG || loggingEvent.level == Level.TRACE)
                        LogLevel.Detail else LogLevel.Info,
                    module = loggingEvent.loggerName.take(80),
                    message = if (text.length > 8192) text.take(8192) + "…[已截断]" else text,
                ))
            }
        }
        KotlinLogging.logger("OhMyFly")
    }

    suspend fun configure(policy: LogPolicy) {
        worker.configure(policy)
        KotlinLoggingConfiguration.direct.logLevel = if (policy.level == LogLevel.Info) Level.INFO else Level.DEBUG
    }
    suspend fun page(level: LogLevel = LogLevel.Detail, cursor: LogCursor? = null): LogPage = worker.page(level, cursor)
    suspend fun clear() = worker.clear()
    suspend fun flush() = worker.flush()

    internal fun initialize(directory: () -> Path): Boolean = worker.start(directory).also { started ->
        if (started) logger.info { "[应用] 启动，日志文件等待已保存的配置" }
    }

    internal fun background() {
        logger.info { "[应用] 进入后台，刷新待写日志" }
        scope.launch { flush() }
    }
}

public fun flushAppLogsInBackground() = AppLog.background()
public fun appLogsForeground() { logger.info { "[应用] 进入前台" } }
