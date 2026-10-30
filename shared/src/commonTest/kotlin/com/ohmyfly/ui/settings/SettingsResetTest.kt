package com.ohmyfly.ui.settings

import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.MediaSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.settings.SpeedSettings
import com.ohmyfly.settings.SubscriptionSettings
import com.ohmyfly.ui.AppMessageKind
import com.ohmyfly.ui.AppMessageState
import com.ohmyfly.ui.ThemeMode
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
class SettingsResetTest {
    private val custom = AppSettings(
        themeMode = ThemeMode.Dark,
        reduceMotion = true,
        speed = SpeedSettings(downloadMiB = 50),
        media = MediaSettings(enabled = true),
        subscription = SubscriptionSettings(userAgent = "Custom"),
        mirrors = listOf(MirrorEntry("custom", "Custom", "https://custom.example/")),
        selectedMirrorId = "custom",
    )

    @Test
    fun pageDefaultsChangeOnlyTheirOwnSettingsAndAllDefaultsReplaceCustomMirrors() {
        assertEquals(custom.copy(speed = SpeedSettings()), SettingsResetTarget.Speed.apply(custom))
        assertEquals(custom.copy(media = MediaSettings()), SettingsResetTarget.Media.apply(custom))
        assertEquals(custom.copy(subscription = SubscriptionSettings()), SettingsResetTarget.Subscription.apply(custom))
        val all = SettingsResetTarget.All.apply(custom)
        assertEquals(AppSettings(), all)
        assertTrue(all.mirrors.isNotEmpty())
        assertFalse(all.mirrors.any { it.id == "custom" })
        assertEquals(AppSettings().selectedMirrorId, all.selectedMirrorId)
    }

    @Test
    fun pageResetPreservesOtherDraftsAndFullResetClearsAllPageDrafts() = runTest {
        val disk = MutableStateFlow(AppSettings())
        val session = SettingsSession(disk) { transform -> disk.value = transform(disk.value) }
        val messages = AppMessageState()
        session.onMessage = messages::show
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val speed = edits.url()
        val subscription = edits.agent()
        speed.open()
        subscription.open()
        edits.input(speed, androidx.compose.ui.text.input.TextFieldValue("invalid"))
        edits.input(subscription, androidx.compose.ui.text.input.TextFieldValue("dirty"))
        edits.reset(SettingsResetTarget.Speed)
        assertEquals("已恢复本页默认设置", messages.current?.text)
        assertFalse(speed.expanded)
        assertEquals(AppSettings().speed.latencyUrl, speed.value.text)
        assertTrue(subscription.expanded)
        assertEquals("dirty", subscription.value.text)
        edits.reset(SettingsResetTarget.All)
        assertFalse(subscription.expanded)
        assertEquals(AppSettings().subscription.userAgent, subscription.value.text)
    }

    @Test
    fun requestingAndCancellingConfirmationNeverWrites() = runTest {
        var writes = 0
        val disk = MutableStateFlow(custom)
        val session = SettingsSession(disk) { transform -> writes++; disk.value = transform(disk.value) }
        val messages = AppMessageState()
        session.onMessage = messages::show
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val state = SettingsResetState()
        state.ask()
        state.cancel()
        state.submit { edits.reset(SettingsResetTarget.All) }
        assertEquals(0, writes)
        assertEquals(custom, disk.value)
        assertEquals(null, messages.current)
    }

    @Test
    fun pendingResetDoesNotClaimSuccessAndDuplicateConfirmCannotWriteTwice() = runTest {
        val disk = MutableStateFlow(custom)
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val session = SettingsSession(disk) { transform -> writes++; gate.await(); disk.value = transform(disk.value) }
        val messages = AppMessageState()
        session.onMessage = messages::show
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val state = SettingsResetState()
        state.ask()
        backgroundScope.launch { state.submit { edits.reset(SettingsResetTarget.All) } }
        runCurrent()
        assertTrue(state.busy)
        assertTrue(state.confirming)
        assertEquals(null, messages.current)
        assertEquals(custom, disk.value)
        assertEquals(AppSettings(), session.state.settings)
        state.submit { edits.reset(SettingsResetTarget.All) }
        assertEquals(1, writes)
        gate.complete(Unit)
        runCurrent()
        assertEquals(AppMessageKind.Success, messages.current?.kind)
        assertEquals("已恢复全部默认设置", messages.current?.text)
        assertFalse(state.confirming)
        assertEquals(AppSettings(), disk.value)
    }

    @Test
    fun failedResetKeepsConfirmationAndCanRetryWithoutLosingOtherFields() = runTest {
        val disk = MutableStateFlow(custom)
        var fail = true
        val session = SettingsSession(disk) { transform ->
            if (fail) error("storage unavailable")
            disk.value = transform(disk.value)
        }
        val messages = AppMessageState()
        session.onMessage = messages::show
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val state = SettingsResetState()
        state.ask()
        state.submit { edits.reset(SettingsResetTarget.Speed) }
        assertEquals(AppMessageKind.Failure, messages.current?.kind)
        assertTrue(state.confirming)
        assertEquals("本页默认设置保存失败，已恢复原值", messages.current?.text)
        assertEquals(custom, disk.value)
        fail = false
        state.submit { edits.reset(SettingsResetTarget.Speed) }
        assertEquals(AppMessageKind.Success, messages.current?.kind)
        assertEquals(custom.copy(speed = SpeedSettings()), disk.value)
    }

    @Test
    fun uncertainResetNeverClaimsSuccessEvenThoughTransactionHasRun() = runTest {
        var disk = custom
        var reads = 0
        val values = flow {
            if (++reads > 1) error("confirmation read failed")
            emit(disk)
            awaitCancellation()
        }
        val session = SettingsSession(values) { transform -> disk = transform(disk) }
        val messages = AppMessageState()
        session.onMessage = messages::show
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val state = SettingsResetState()
        state.ask()
        state.submit { edits.reset(SettingsResetTarget.All) }
        assertEquals(AppSettings(), disk)
        assertTrue(session.state.writeUncertain)
        assertEquals(null, messages.current)
        assertTrue(state.confirming)
        assertFalse(state.busy)
    }
}
