// 查询和清理用受控挂起验证竞态，窗口回收后仍可双向读取。
package com.ohmyfly.ui.settings

import com.ohmyfly.logging.LogEntry
import com.ohmyfly.logging.LogFileCursor
import com.ohmyfly.logging.LogFileInfo
import com.ohmyfly.logging.LogFilePage
import com.ohmyfly.logging.LogLevel
import com.ohmyfly.ui.AppMessageKind
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
    private val file = LogFileInfo("f", 1, 100, "g")
    private fun row(id: String) = LogEntry(id, 1, LogLevel.Info, "test", id)
    private fun page(index: Long) = LogFilePage(listOf(row("$index")),
        older = if (index == 0L) null else LogFileCursor(index - 1),
        newer = if (index == 9L) null else LogFileCursor(index + 1, forward = true))

    @Test fun navigationReplacesRowsInsteadOfAccumulatingHistory() = runTest {
        val state = LogPageState(read = { _, _, at -> page(at?.offset ?: 9) })
        state.select(file)
        repeat(9) { state.more(false) }
        assertEquals(listOf("2", "1", "0"), state.entries.map { it.id })
        repeat(7) { state.more(true) }
        assertEquals(listOf("9", "8", "7"), state.entries.map { it.id })
        assertTrue(state.pages.size <= 3)
    }

    @Test fun clearAndReadCannotRaceToRestoreOldRows() = runTest {
        val gate = CompletableDeferred<LogFilePage>()
        var clears = 0
        val state = LogPageState(read = { _, _, _ -> gate.await() }, erase = { clears++ })
        val reading = launch { state.select(file) }
        runCurrent()
        assertTrue(state.clear())
        gate.complete(LogFilePage(listOf(row("old"))))
        reading.join()
        assertEquals(1, clears)
        assertTrue(state.entries.isEmpty())
        assertEquals(null, state.selected)
    }

    @Test fun expiredCursorClearsHistoryAndRequiresRefresh() = runTest {
        val state = LogPageState(read = { _, _, at ->
            if (at == null) page(9) else LogFilePage(emptyList(), invalidated = true)
        })
        state.select(file)
        state.more(false)
        assertTrue(state.entries.isEmpty())
        assertEquals(null, state.newer)
        assertNotNull(state.failure)
    }

    @Test fun failureRemainsVisibleAndLevelSelectionReachesReader() = runTest {
        var level = LogLevel.Detail
        val state = LogPageState(read = { _, selected, _ -> level = selected; error("disk") })
        state.select(file)
        state.changeLevel(LogLevel.Info)
        assertEquals(LogLevel.Info, level)
        assertNotNull(state.failure)
        assertFalse(state.busy)
    }

    @Test fun switchingFilesRejectsLateResultAndBusyReset() = runTest {
        val gate = CompletableDeferred<LogFilePage>()
        val second = file.copy(id = "second")
        val state = LogPageState(read = { target, _, _ ->
            if (target.id == file.id) gate.await() else LogFilePage(listOf(row(target.id)))
        })
        val old = launch { state.select(file) }
        runCurrent()
        state.select(second)
        gate.complete(LogFilePage(listOf(row("stale"))))
        old.join()
        assertEquals(listOf("second"), state.entries.map { it.id })
        assertFalse(state.busy)
    }

    @Test fun levelChangeRejectsEarlierLevelResult() = runTest {
        val gate = CompletableDeferred<LogFilePage>()
        val state = LogPageState(read = { _, level, _ ->
            if (level == LogLevel.Detail) gate.await() else LogFilePage(listOf(row("info")))
        })
        val old = launch { state.select(file) }
        runCurrent()
        state.changeLevel(LogLevel.Info)
        gate.complete(LogFilePage(listOf(row("detail"))))
        old.join()
        assertEquals(listOf("info"), state.entries.map { it.id })
    }

    @Test fun clearFailureKeepsContentsAndReportsFailure() = runTest {
        val messages = mutableListOf<AppMessageKind>()
        val gate = CompletableDeferred<Unit>()
        val state = LogPageState(read = { _, _, _ -> page(9) }, erase = { gate.await(); error("disk") },
            notify = { kind, _ -> messages += kind })
        state.select(file)
        val clear = launch { assertFalse(state.clear()) }
        runCurrent()
        assertTrue(state.clearing)
        assertTrue(state.busy)
        gate.complete(Unit)
        clear.join()
        assertEquals(listOf("9"), state.entries.map { it.id })
        assertEquals(listOf(AppMessageKind.Failure), messages)
        assertFalse(state.busy)
    }

    @Test fun refreshFailureKeepsFilesAndLoadedText() = runTest {
        var fail = false
        val state = LogPageState(enumerate = { if (fail) error("disk") else listOf(file) },
            read = { _, _, _ -> page(9) })
        state.refresh()
        state.select(file)
        fail = true
        assertFalse(state.refresh())
        assertEquals(listOf(file), state.files)
        assertEquals(listOf("9"), state.entries.map { it.id })
    }

    @Test fun configurationRefreshRejectsOldFileSnapshot() = runTest {
        val gate = CompletableDeferred<LogFilePage>()
        val updated = file.copy(generation = "new")
        val state = LogPageState(enumerate = { listOf(updated) }, read = { target, _, _ ->
            if (target.generation == "g") gate.await() else page(8)
        })
        val old = launch { state.select(file) }
        runCurrent()
        state.refresh(invalidate = true)
        gate.complete(page(9))
        old.join()
        assertEquals(listOf("8"), state.entries.map { it.id })
        assertEquals(updated, state.selected)
    }

    @Test fun emptyScansAdvanceWithoutEvictingLoadedMatches() = runTest {
        val state = LogPageState(read = { _, _, at ->
            if (at == null) page(9) else LogFilePage(emptyList(), older =
                if (at.offset == 0L) null else LogFileCursor(at.offset - 1))
        })
        state.select(file)
        repeat(9) { state.more(false) }
        assertEquals(listOf("9"), state.entries.map { it.id })
        assertEquals(null, state.older)
    }

    @Test fun clearSuccessEmptiesListAndEmitsOneSuccess() = runTest {
        val messages = mutableListOf<AppMessageKind>()
        val state = LogPageState(enumerate = { listOf(file) }, read = { _, _, _ -> page(9) }, erase = {},
            notify = { kind, _ -> messages += kind })
        state.refresh()
        state.select(file)
        assertTrue(state.clear())
        assertTrue(state.files.isEmpty())
        assertTrue(state.entries.isEmpty())
        assertEquals(listOf(AppMessageKind.Success), messages)
    }
}
