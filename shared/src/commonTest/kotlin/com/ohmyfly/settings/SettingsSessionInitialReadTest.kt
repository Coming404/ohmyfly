package com.ohmyfly.settings

import com.ohmyfly.ui.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSessionInitialReadTest {
    @Test
    fun changesBeforeTheFirstReadNeverWriteDefaults() = runTest {
        val stored = AppSettings(themeMode = ThemeMode.Dark)
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val values = flow {
            gate.await()
            emit(stored)
            awaitCancellation()
        }
        val session = SettingsSession(values) { writes++ }
        backgroundScope.launch { session.run() }
        runCurrent()

        session.change { it.copy(themeMode = ThemeMode.Light) }
        assertEquals(AppSettings(), session.state.settings)
        assertEquals(0, writes)

        gate.complete(Unit)
        runCurrent()
        assertEquals(stored, session.state.settings)
        assertEquals(stored, session.state.confirmedSettings)
        assertEquals(0, writes)
    }

    @Test
    fun retryAfterInitialReadFailureRestoresWritingWithoutOverwritingStoredData() = runTest {
        val stored = AppSettings(themeMode = ThemeMode.Dark)
        var reads = 0
        var writes = 0
        val values = flow {
            reads++
            if (reads == 1) error("read failed")
            emit(stored)
            awaitCancellation()
        }
        val session = SettingsSession(values) { writes++ }
        backgroundScope.launch { session.run() }
        runCurrent()

        assertTrue(session.state.readFailed)
        assertFalse(session.state.writeUncertain)
        assertEquals(AppSettings(), session.state.settings)
        session.change { it.copy(themeMode = ThemeMode.Light) }
        runCurrent()
        assertEquals(0, writes)

        session.retryRead()
        runCurrent()
        assertFalse(session.state.readFailed)
        assertFalse(session.state.writeUncertain)
        assertEquals(stored, session.state.settings)
        assertEquals(stored, session.state.confirmedSettings)
        session.change { it.copy(reduceMotion = true) }
        runCurrent()
        assertEquals(1, writes)
    }
}
