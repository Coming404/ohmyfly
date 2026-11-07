// 验证真实 logger 采集日期与消息上限，避免平台事件时间被误当作日期。
package com.ohmyfly.logging

import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class AppLogAppenderTest {
    @Test
    fun realLoggerStoresEpochTimeAndKeepsTheBoundedMessage() {
        val logger = AppLog.logger
        val previousAppender = KotlinLoggingConfiguration.direct.appender
        val previousLevel = KotlinLoggingConfiguration.direct.logLevel
        val entries = mutableListOf<LogEntry>()
        KotlinLoggingConfiguration.direct.appender = AppLogAppender(entries::add)
        KotlinLoggingConfiguration.direct.logLevel = Level.DEBUG
        try {
            val before = Clock.System.now().toEpochMilliseconds()
            logger.info { "业务记录" }
            logger.debug { "详".repeat(9000) }
            val after = Clock.System.now().toEpochMilliseconds()
            assertEquals(2, entries.size)
            assertTrue(entries.all { it.timestampMillis in before..after })
            assertEquals("业务记录", entries[0].message)
            assertEquals(LogLevel.Info, entries[0].level)
            assertEquals(LogLevel.Detail, entries[1].level)
            assertEquals("详".repeat(8192) + "…[已截断]", entries[1].message)
        } finally {
            KotlinLoggingConfiguration.direct.appender = previousAppender
            KotlinLoggingConfiguration.direct.logLevel = previousLevel
        }
    }
}
