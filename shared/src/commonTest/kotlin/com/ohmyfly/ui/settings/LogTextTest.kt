// 日志文本转换使用真实毫秒日期，复制范围精确等于已加载窗口。
package com.ohmyfly.ui.settings

import com.ohmyfly.logging.LogEntry
import com.ohmyfly.logging.LogFileInfo
import com.ohmyfly.logging.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LogTextTest {
    @Test fun timeAndFileLabelsPreserveRealEpochAndSameSecondIdentity() {
        val file = LogFileInfo("first", 1_700_000_000_001, 1024, "g")
        assertEquals("2023-11-14 22:13:20.001 UTC", logTime(file.createdMillis))
        assertNotEquals(logFileName(file), logFileName(file.copy(id = "second", createdMillis = file.createdMillis + 1)))
        assertEquals("2023-11-14-22-13-20.001.log", logFileName(file))
        assertEquals("1 KiB", logBytes(file.bytes))
    }

    @Test fun copyContainsOnlyLoadedEntriesAndPreservesMultilineBody() {
        val row = LogEntry("one", 1_700_000_000_001, LogLevel.Info, "test", "第一行\n第二行")
        assertEquals("2023-11-14 22:13:20.001 UTC  信息\n第一行\n第二行", logText(listOf(row)))
        assertEquals("", logText(emptyList()))
    }
}
