// 两个边缘同时可见时只沿用户方向扫描，EOF和程序定位均不触发反向读取。
package com.ohmyfly.ui.settings

import com.ohmyfly.logging.LogEntry
import com.ohmyfly.logging.LogFileCursor
import com.ohmyfly.logging.LogFileInfo
import com.ohmyfly.logging.LogFilePage
import com.ohmyfly.logging.LogLevel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogReadDirectionTest {
    private val file = LogFileInfo("f", 1, 100, "g")
    private fun page(index: Long, empty: Boolean = false) = LogFilePage(
        if (empty) emptyList() else listOf(LogEntry("$index", 1, LogLevel.Info, "t", "$index")),
        older = if (index == 0L) null else LogFileCursor(index - 1),
        newer = if (index == 9L) null else LogFileCursor(index + 1, true),
    )

    @Test fun sparseMatchesStopAtBoundaryAndOnlyUserCanReverse() = runTest {
        val reads = mutableListOf<LogFileCursor?>()
        val state = LogPageState(read = { _, _, cursor -> reads += cursor; page(cursor?.offset ?: 9) })
        val direction = LogReadDirection()
        state.select(file)
        repeat(20) {
            direction.request(true, true, state.older, state.newer)?.let { state.more(it.forward) }
        }
        assertEquals(10, reads.size)
        assertEquals(listOf("2", "1", "0"), state.entries.map { it.id })
        assertNull(direction.request(true, true, state.older, state.newer))
        direction.userScroll(10f)
        repeat(20) {
            direction.request(true, true, state.older, state.newer)?.let { state.more(it.forward) }
        }
        assertEquals(17, reads.size)
        assertEquals(listOf("9", "8", "7"), state.entries.map { it.id })
        assertNull(direction.request(true, true, state.older, state.newer))
        direction.userScroll(-10f)
        direction.request(true, true, state.older, state.newer)?.let { state.more(it.forward) }
        assertEquals(listOf("8", "7", "6"), state.entries.map { it.id })
    }

    @Test fun noMatchesStillAdvanceToEofWithoutReverseRequests() = runTest {
        var reads = 0
        val state = LogPageState(read = { _, _, cursor -> reads++; page(cursor?.offset ?: 9, empty = true) })
        val direction = LogReadDirection()
        state.select(file)
        repeat(20) {
            direction.request(true, true, state.older, state.newer)?.let { state.more(it.forward) }
        }
        assertEquals(10, reads)
        assertTrue(state.entries.isEmpty())
        assertNull(state.older)
        assertNull(direction.request(true, true, state.older, state.newer))
    }

    @Test fun programmaticAnchorsAndOppositeEdgeDoNotChooseDirection() {
        val direction = LogReadDirection()
        val older = LogFileCursor(10)
        val newer = LogFileCursor(30, true)
        assertNull(direction.request(true, false, older, newer))
        direction.userScroll(1f)
        direction.userScroll(0f)
        assertEquals(newer, direction.request(true, true, older, newer))
        assertNull(direction.request(false, true, older, newer))
    }
}
