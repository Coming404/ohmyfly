// 查询和清理用挂起操作验证竞态；不依赖真实磁盘或 UI 重组。
package com.ohmyfly.ui.settings

import com.ohmyfly.logging.LogCursor
import com.ohmyfly.logging.LogEntry
import com.ohmyfly.logging.LogLevel
import com.ohmyfly.logging.LogPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LogPageStateTest {
    private fun row(id: String) = LogEntry(id, 1, LogLevel.Info, "test", id)

    @Test fun navigationReplacesRowsInsteadOfAccumulatingHistory() = runTest {
        val cursor = LogCursor("g", "f", 50)
        val state = LogPageState(read = { _, at ->
            if (at == null) LogPage(listOf(row("new")), cursor) else LogPage(listOf(row("old")), null)
        })
        state.refresh()
        state.older()
        assertEquals(listOf("old"), state.entries.map { it.id })
        state.newer()
        assertEquals(listOf("new"), state.entries.map { it.id })
    }

    @Test fun clearAndReadCannotRaceToRestoreOldRows() = runTest {
        val gate = CompletableDeferred<LogPage>()
        var clears = 0
        val state = LogPageState(read = { _, _ -> gate.await() }, erase = { clears++ })
        val reading = launch { state.refresh() }
        runCurrent()
        assertFalse(state.clear())
        gate.complete(LogPage(listOf(row("old")), null))
        reading.join()
        assertTrue(state.clear())
        assertEquals(1, clears)
        assertTrue(state.entries.isEmpty())
    }

    @Test fun expiredCursorClearsHistoryAndRequiresRefresh() = runTest {
        val state = LogPageState(read = { _, at ->
            if (at == null) LogPage(listOf(row("new")), LogCursor("g", "f", 20))
            else LogPage(emptyList(), null, invalidated = true)
        })
        state.refresh()
        state.older()
        assertTrue(state.entries.isEmpty())
        assertFalse(state.canGoBack)
        assertNotNull(state.failure)
    }

    @Test fun failureRemainsVisibleAndLevelSelectionReachesReader() = runTest {
        var level = LogLevel.Detail
        val state = LogPageState(read = { selected, _ -> level = selected; error("disk") })
        state.refresh(LogLevel.Info)
        assertEquals(LogLevel.Info, level)
        assertNotNull(state.failure)
        assertFalse(state.busy)
    }
}
