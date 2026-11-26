// 日志文本转换使用真实毫秒日期，复制范围精确等于已加载窗口。
package com.ohmyfly.ui.settings

import com.ohmyfly.logging.LogEntry
import com.ohmyfly.logging.LogFileInfo
import com.ohmyfly.logging.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LogTextTest {
    @Test fun timeAndFileLabelsPreserveRealEpochAndSameSecondIdentity() {
        val file = LogFileInfo("first", 1_700_000_000_001, 1024, "g")
        assertEquals("2023-11-15 06:13:20", logTime(file.createdMillis))
        assertEquals("20231115-061320.log", logFileName(file))
        val next = file.copy(id = "second", createdMillis = file.createdMillis + 1)
        val third = file.copy(id = "third", createdMillis = file.createdMillis + 2)
        val labels = logFileNames(listOf(third, next, file))
        assertEquals("20231115-061320.log", labels[file.id])
        assertEquals("20231115-061320-2.log", labels[next.id])
        assertEquals("20231115-061320-3.log", labels[third.id])
        assertEquals(labels, logFileNames(listOf(file, next, third)))
        assertEquals("1 KiB", logBytes(file.bytes))
        val newYear = Instant.parse("2026-12-31T16:00:00Z").toEpochMilliseconds()
        assertEquals("2027-01-01 00:00:00", logTime(newYear))
        assertEquals("20270101-000000.log", logFileName(file.copy(createdMillis = newYear)))
    }

    @Test fun copyContainsOnlyLoadedEntriesAndPreservesMultilineBody() {
        val row = LogEntry("one", 1_700_000_000_001, LogLevel.Info, "test", "第一行\n第二行")
        assertEquals("2023-11-15 06:13:20  信息\n第一行\n第二行", logText(listOf(row)))
        assertEquals("", logText(emptyList()))
    }
}
