// 统一安装文件 Appender；业务直接使用 kotlin-logging 的 info/debug 调用。
package com.ohmyfly.logging

import io.github.oshai.kotlinlogging.DirectLoggerFactory
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
        KotlinLoggingConfiguration.direct.appender = AppLogAppender(worker::enqueue)
        KotlinLogging.logger("OhMyFly")
    }

    suspend fun configure(policy: LogPolicy) {
        worker.configure(policy)
        KotlinLoggingConfiguration.direct.logLevel = if (policy.level == LogLevel.Info) Level.INFO else Level.DEBUG
    }
    suspend fun files(): List<LogFileInfo> = worker.files()
    suspend fun filePage(file: LogFileInfo, level: LogLevel, cursor: LogFileCursor?): LogFilePage =
        worker.filePage(file, level, cursor)
    suspend fun clear() = worker.clear()
    suspend fun flush() = worker.flush()

    internal fun initialize(directory: () -> Path): Boolean = worker.start(directory).also { started ->
        if (started) logger.info { "[应用] 启动，日志文件等待已保存的配置" }
    }

    internal fun background() {
        scope.launch { flush() }
    }
}

public fun flushAppLogsInBackground() = AppLog.background()
