// 在日志采集边界记录真实日期，文件写入继续交给后台队列。
package com.ohmyfly.logging

import io.github.oshai.kotlinlogging.Appender
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.Level
import kotlin.time.Clock

internal class AppLogAppender(private val append: (LogEntry) -> Unit) : Appender {
    override fun log(loggingEvent: KLoggingEvent) {
        if (loggingEvent.level == Level.OFF) return
        // 不保存异常和任意负载；事件时间在采集边界取系统时钟，不能使用平台相对计时。
        val text = loggingEvent.message.orEmpty()
        append(LogEntry(
            id = newLogId(), timestampMillis = Clock.System.now().toEpochMilliseconds(),
            level = if (loggingEvent.level == Level.DEBUG || loggingEvent.level == Level.TRACE)
                LogLevel.Detail else LogLevel.Info,
            module = loggingEvent.loggerName.take(80),
            message = if (text.length > 8192) text.take(8192) + "…[已截断]" else text,
        ))
    }
}
