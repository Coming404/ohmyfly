package com.ohmyfly.settings

import com.ohmyfly.ui.AppMessageKind
import com.ohmyfly.ui.AppMessageState
import com.ohmyfly.ui.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSessionTest {
    @Test
    fun changeIsVisibleBeforeTheDiskWriteStarts() = runTest {
        val messages = AppMessageState()
        val values = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val session = SettingsSession(values) { transform ->
            writes++
            gate.await()
            values.value = transform(values.value)
        }
        backgroundScope.launch { session.run() }
        runCurrent()

        session.onMessage = messages::show
        session.change { it.copy(materialMode = MaterialMode.SoftGlass) }
        assertEquals(MaterialMode.SoftGlass, session.state.settings.materialMode)
        assertEquals(MaterialMode.LiquidGlass, values.value.materialMode)
        assertEquals(0, writes)
        runCurrent()
        assertEquals(1, writes)
        gate.complete(Unit)
        runCurrent()
        assertEquals(values.value, session.state.settings)
        assertEquals(AppMessageKind.Success, messages.current?.kind)
    }

    @Test
    fun rapidChangesPreserveOrderAndOtherFields() = runTest {
        val values = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        val committed = mutableListOf<AppSettings>()
        val session = SettingsSession(values) { transform ->
            gate.await()
            values.value = transform(values.value)
            committed += values.value
        }
        backgroundScope.launch { session.run() }
        runCurrent()
        session.change { it.copy(themeMode = ThemeMode.Dark) }
        session.change { it.copy(materialMode = MaterialMode.HighlightGlass) }
        session.change { it.copy(reduceTransparency = true) }
        assertEquals(ThemeMode.Dark, session.state.settings.themeMode)
        assertEquals(MaterialMode.HighlightGlass, session.state.settings.materialMode)
        assertEquals(true, session.state.settings.reduceTransparency)
        runCurrent()
        // An independent transaction must survive: save transforms, not full UI snapshots.
        values.value = values.value.copy(speed = values.value.speed.copy(downloadMiB = 32))
        gate.complete(Unit)
        runCurrent()
        assertEquals(3, committed.size)
        assertEquals(ThemeMode.Dark, committed[0].themeMode)
        assertEquals(MaterialMode.LiquidGlass, committed[0].materialMode)
        assertEquals(MaterialMode.HighlightGlass, committed[1].materialMode)
        assertTrue(values.value.reduceTransparency)
        assertEquals(32, values.value.speed.downloadMiB)
        assertEquals(values.value, session.state.settings)
    }

    @Test
    fun oldFlowValuesCannotOverwritePendingChoice() = runTest {
        val values = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        val session = SettingsSession(values) { transform ->
            gate.await()
            values.value = transform(values.value)
        }
        backgroundScope.launch { session.run() }
        runCurrent()
        session.change { it.copy(themeMode = ThemeMode.Dark) }
        values.value = values.value.copy(themeMode = ThemeMode.Light)
        runCurrent()
        assertEquals(ThemeMode.Dark, session.state.settings.themeMode)
        gate.complete(Unit)
        runCurrent()
        assertEquals(ThemeMode.Dark, session.state.settings.themeMode)
    }

    @Test
    fun returningToInitialValueDoesNotNeedAnEqualityEcho() = runTest {
        val values = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val session = SettingsSession(values) { transform ->
            gate.await()
            values.value = transform(values.value)
            writes++
        }
        backgroundScope.launch { session.run() }
        runCurrent()
        session.change { it.copy(themeMode = ThemeMode.Dark) }
        session.change { it.copy(themeMode = ThemeMode.System) }
        assertEquals(ThemeMode.System, session.state.settings.themeMode)
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, writes)
        assertEquals(values.value, session.state.settings)
    }

    @Test
    fun invalidInputDoesNotChangeStateOrWrite() = runTest {
        val values = MutableStateFlow(AppSettings())
        var writes = 0
        val session = SettingsSession(values) { writes++ }
        backgroundScope.launch { session.run() }
        runCurrent()
        val messages = AppMessageState()
        session.onMessage = messages::show
        session.change { it.copy(speed = it.speed.copy(downloadMiB = -1)) }
        assertEquals(values.value, session.state.settings)
        assertEquals(AppMessageKind.Error, messages.current?.kind)
        runCurrent()
        assertEquals(0, writes)
        messages.dismiss()
        assertEquals(null, messages.current)
    }

    @Test
    fun failedWriteDiscardsDependentSuffixAndAllowsReselection() = runTest {
        val values = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val session = SettingsSession(values) { transform ->
            gate.await()
            writes++
            if (writes == 2) error("storage unavailable")
            values.value = transform(values.value)
        }
        backgroundScope.launch { session.run() }
        runCurrent()
        val messages = AppMessageState()
        session.onMessage = messages::show
        session.change { it.copy(themeMode = ThemeMode.Dark) }
        session.change { it.copy(materialMode = MaterialMode.SoftGlass) }
        session.change { it.copy(reduceTransparency = true) }
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, writes)
        assertEquals(ThemeMode.Dark, session.state.settings.themeMode)
        assertEquals(MaterialMode.LiquidGlass, session.state.settings.materialMode)
        assertEquals(false, session.state.settings.reduceTransparency)
        assertEquals(AppMessageKind.Failure, messages.current?.kind)
        assertEquals(values.value, session.state.settings)
        session.change { it.copy(materialMode = MaterialMode.HighlightGlass) }
        assertEquals(AppMessageKind.Failure, messages.current?.kind)
        runCurrent()
        assertEquals(AppMessageKind.Success, messages.current?.kind)
        assertEquals(3, writes)
        assertEquals(MaterialMode.HighlightGlass, values.value.materialMode)
        assertEquals(values.value, session.state.settings)
    }

    @Test
    fun cancelledWriteIsNotReportedAsStorageFailure() = runTest {
        val values = MutableStateFlow(AppSettings())
        var cancel = true
        val session = SettingsSession(values) { transform ->
            if (cancel) throw CancellationException("cancelled")
            values.value = transform(values.value)
        }
        backgroundScope.launch { session.run() }
        runCurrent()
        val messages = AppMessageState()
        session.onMessage = messages::show
        session.change { it.copy(themeMode = ThemeMode.Dark) }
        runCurrent()
        assertEquals(null, messages.current)
        assertFalse(session.state.readFailed)
        assertEquals(values.value, session.state.settings)
        cancel = false
        session.change { it.copy(themeMode = ThemeMode.Light) }
        runCurrent()
        assertEquals(ThemeMode.Light, values.value.themeMode)
    }

    @Test
    fun readFailureAndFutureSchemaNeverWriteDefaults() = runTest {
        for (failure in listOf(IllegalStateException("read failed"), SettingsSchemaException(99, 1))) {
            var writes = 0
            val values = flow<AppSettings> { throw failure }
            val session = SettingsSession(values) { writes++ }
            val job = backgroundScope.launch { session.run() }
            runCurrent()
            assertTrue(session.state.readFailed)
            assertFalse(session.state.writeUncertain)
            assertEquals(AppSettings(), session.state.settings)
            session.change { AppSettings() }
            runCurrent()
            assertEquals(0, writes)
            job.cancel()
        }
    }

    @Test
    fun oldSessionCannotPublishOrWriteAfterItsLifetimeEnds() = runTest {
        val values = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        val old = SettingsSession(values) { transform ->
            gate.await()
            values.value = transform(values.value)
        }
        val oldJob = backgroundScope.launch { old.run() }
        runCurrent()
        old.change { it.copy(themeMode = ThemeMode.Dark) }
        runCurrent()
        oldJob.cancel()
        runCurrent()
        val replacement = SettingsSession(values) { transform -> values.value = transform(values.value) }
        backgroundScope.launch { replacement.run() }
        runCurrent()
        gate.complete(Unit)
        old.change { it.copy(reduceTransparency = true) }
        runCurrent()
        assertEquals(AppSettings(), values.value)
        assertEquals(values.value, replacement.state.settings)
    }

    @Test
    fun confirmationReadFailureBlocksFurtherWritesWithoutPretendingRollback() = runTest {
        val initial = AppSettings()
        var subscriptions = 0
        var committed = initial
        var writes = 0
        val values = flow {
            subscriptions++
            if (subscriptions == 1) {
                emit(initial)
                awaitCancellation()
            } else {
                error("confirmation read failed")
            }
        }
        val session = SettingsSession(values) { transform ->
            committed = transform(committed)
            writes++
        }
        backgroundScope.launch { session.run() }
        runCurrent()
        session.change { it.copy(themeMode = ThemeMode.Dark) }
        session.change { it.copy(materialMode = MaterialMode.SoftGlass) }
        runCurrent()
        assertEquals(1, writes)
        assertEquals(ThemeMode.Dark, committed.themeMode)
        assertEquals(MaterialMode.LiquidGlass, committed.materialMode)
        assertTrue(session.state.readFailed)
        assertTrue(session.state.writeUncertain)
        session.change { it.copy(reduceTransparency = true) }
        runCurrent()
        assertEquals(1, writes)
    }

}
