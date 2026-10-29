package com.ohmyfly.ui.settings

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.settings.SettingsWriteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsInputRecoveryTest {
    @Test
    fun collapseStartsSavingImmediatelyAndClosesOnlyAfterConfirmation() = runTest {
        val disk = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val session = SettingsSession(disk) { transform -> writes++; gate.await(); disk.value = transform(disk.value) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val draft = edits.agent()
        draft.open()
        edits.input(draft, TextFieldValue("Agent/2"))
        edits.finish(draft, SettingFinish.Collapse)
        assertTrue(draft.saving)
        runCurrent()
        assertEquals(1, writes)
        assertTrue(draft.expanded)
        gate.complete(Unit)
        runCurrent()
        assertFalse(draft.expanded)
        assertEquals("Agent/2", disk.value.subscription.userAgent)
    }

    @Test
    fun forcedResetClearsInvalidDraftEvenWhenFactoryValueIsAlreadyStored() = runTest {
        val disk = MutableStateFlow(AppSettings())
        var writes = 0
        val session = SettingsSession(disk) { transform -> writes++; disk.value = transform(disk.value) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val draft = edits.url()
        draft.open()
        edits.input(draft, TextFieldValue("invalid"))
        edits.finish(draft, SettingFinish.Collapse)
        assertNotNull(draft.error)
        assertIs<SettingsWriteResult.Confirmed>(edits.reset(SettingsResetTarget.Speed))
        runCurrent()
        assertEquals(1, writes)
        assertFalse(draft.expanded)
        assertNull(draft.error)
        assertEquals(disk.value.speed.latencyUrl, draft.value.text)
    }

    @Test
    fun externalResetDoesNotInterruptCompositionOrCommitObsoleteInput() = runTest {
        val disk = MutableStateFlow(AppSettings())
        var writes = 0
        val session = SettingsSession(disk) { transform -> writes++; disk.value = transform(disk.value) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val draft = edits.agent()
        draft.open()
        val composing = TextFieldValue("输入", TextRange(2), TextRange(0, 2))
        edits.input(draft, composing)
        edits.finish(draft, SettingFinish.Done)
        edits.reset(SettingsResetTarget.Subscription)
        assertSame(composing, draft.value)
        edits.input(draft, composing.copy(composition = null))
        runCurrent()
        assertEquals(1, writes)
        assertEquals(AppSettings().subscription.userAgent, draft.value.text)
        assertFalse(draft.expanded)
    }

    @Test
    fun confirmedExternalValueReplacesDirtyTextButUnrelatedChangesDoNot() = runTest {
        val disk = MutableStateFlow(AppSettings())
        val session = SettingsSession(disk) { transform -> disk.value = transform(disk.value) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val draft = edits.agent()
        val input = TextFieldValue("dirty", selection = TextRange(2))
        draft.open()
        edits.input(draft, input)
        edits.sync(disk.value.copy(reduceMotion = true))
        assertSame(input, draft.value)
        disk.value = disk.value.copy(subscription = disk.value.subscription.copy(userAgent = "external"))
        runCurrent()
        edits.sync(checkNotNull(session.state.confirmedSettings))
        assertEquals("external", draft.value.text)
        edits.finish(draft, SettingFinish.Blur)
        runCurrent()
        assertEquals("external", disk.value.subscription.userAgent)
    }

    @Test
    fun uncertainWriteKeepsDraftAndBlocksResubmissionUntilActualReread() = runTest {
        var disk = AppSettings()
        var reads = 0
        var failRead = true
        var writes = 0
        val values = flow {
            reads++
            if (reads > 1 && failRead) error("confirmation read failed")
            emit(disk)
            awaitCancellation()
        }
        val session = SettingsSession(values) { transform -> writes++; disk = transform(disk) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val draft = edits.agent()
        draft.open()
        edits.input(draft, TextFieldValue("Agent/2"))
        edits.finish(draft, SettingFinish.Collapse)
        runCurrent()
        assertTrue(session.state.writeUncertain)
        assertTrue(draft.expanded)
        assertEquals("Agent/2", draft.value.text)
        assertNotNull(draft.error)
        edits.finish(draft, SettingFinish.Retry)
        runCurrent()
        assertEquals(1, writes)
        failRead = false
        session.retryRead()
        runCurrent()
        edits.sync(checkNotNull(session.state.confirmedSettings))
        assertFalse(session.state.readFailed)
        assertNull(draft.error)
        assertEquals("Agent/2", draft.value.text)
        edits.finish(draft, SettingFinish.Blur)
        runCurrent()
        assertEquals(1, writes)
    }

    @Test
    fun losingFocusQueuesTheWriteBeforeTheEditorScopeIsDisposed() = runTest {
        val disk = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        val session = SettingsSession(disk) { transform -> gate.await(); disk.value = transform(disk.value) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val editorJob = Job(backgroundScope.coroutineContext[Job])
        val edits = SettingsEdits(session, CoroutineScope(backgroundScope.coroutineContext + editorJob))
        val draft = edits.agent()
        draft.open()
        edits.focus(draft, true)
        edits.input(draft, TextFieldValue("Agent/2"))
        edits.focus(draft, false)
        editorJob.cancel()
        runCurrent()
        assertEquals(1, session.state.pendingWrites)
        gate.complete(Unit)
        runCurrent()
        assertEquals("Agent/2", disk.value.subscription.userAgent)
        assertEquals(0, session.state.pendingWrites)
    }

    @Test
    fun inactiveSessionRejectsWithoutDiscardingInputOrPretendingToSave() = runTest {
        val disk = MutableStateFlow(AppSettings())
        var writes = 0
        val session = SettingsSession(disk) { writes++ }
        val edits = SettingsEdits(session, backgroundScope)
        val draft = edits.agent()
        draft.open()
        edits.input(draft, TextFieldValue("Agent/2"))
        edits.finish(draft, SettingFinish.Done)
        runCurrent()
        assertEquals(0, writes)
        assertTrue(draft.expanded)
        assertNotNull(draft.error)
        assertEquals("Agent/2", draft.value.text)
    }
}
