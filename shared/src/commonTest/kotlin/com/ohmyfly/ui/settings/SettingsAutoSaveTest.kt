package com.ohmyfly.ui.settings

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsAutoSaveTest {
    @Test
    fun validTypingAndSelectionOnlyKeepOneDraftWithoutWriting() = runTest {
        val disk = MutableStateFlow(AppSettings())
        var writes = 0
        val session = SettingsSession(disk) { transform -> writes++; disk.value = transform(disk.value) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val draft = edits.url()
        draft.open()
        val input = TextFieldValue("https://exa", selection = TextRange(8))
        edits.input(draft, input)
        edits.sync(disk.value.copy(reduceMotion = true))
        runCurrent()
        assertSame(input, draft.value)
        assertSame(draft, edits.url())
        assertTrue(draft.expanded)
        assertEquals(0, writes)
        assertEquals(AppSettings().speed.latencyUrl, disk.value.speed.latencyUrl)
    }

    @Test
    fun doneAndBlurWaitForActualConfirmationAndWriteOnlyOnce() = runTest {
        val disk = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val session = SettingsSession(disk) { transform -> writes++; gate.await(); disk.value = transform(disk.value) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val draft = edits.url()
        val input = TextFieldValue("https://example.com/test", selection = TextRange(9))
        draft.open()
        edits.focus(draft, true)
        edits.input(draft, input)
        edits.finish(draft, SettingFinish.Done)
        edits.focus(draft, false)
        edits.finish(draft, SettingFinish.Done)
        runCurrent()
        assertEquals(1, writes)
        assertTrue(draft.saving)
        assertTrue(draft.expanded)
        assertEquals(input.text, session.state.settings.speed.latencyUrl)
        assertEquals(AppSettings().speed.latencyUrl, session.state.confirmedSettings!!.speed.latencyUrl)
        gate.complete(Unit)
        runCurrent()
        assertFalse(draft.saving)
        assertNull(draft.error)
        assertSame(input, draft.value)
        assertEquals(input.text, disk.value.speed.latencyUrl)
    }

    @Test
    fun compositionSurvivesDoneAndBlurAndCommitsOnlyAfterImeFinishes() = runTest {
        val disk = MutableStateFlow(AppSettings())
        var writes = 0
        val session = SettingsSession(disk) { transform -> writes++; disk.value = transform(disk.value) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val draft = edits.agent()
        draft.open()
        edits.focus(draft, true)
        val composing = TextFieldValue("输入", TextRange(2), TextRange(0, 2))
        edits.input(draft, composing)
        edits.finish(draft, SettingFinish.Done)
        edits.focus(draft, false)
        runCurrent()
        assertSame(composing, draft.value)
        assertEquals(0, writes)
        val finished = composing.copy(composition = null)
        edits.input(draft, finished)
        runCurrent()
        assertEquals(1, writes)
        assertSame(finished, draft.value)
        assertEquals("输入", disk.value.subscription.userAgent)
    }

    @Test
    fun invalidFullValueKeepsOriginalAndCannotCollapse() = runTest {
        val disk = MutableStateFlow(AppSettings())
        var writes = 0
        val session = SettingsSession(disk) { transform -> writes++; disk.value = transform(disk.value) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val draft = edits.url()
        draft.open()
        edits.input(draft, TextFieldValue("http://bad.example/#fragment"))
        edits.finish(draft, SettingFinish.Done)
        edits.finish(draft, SettingFinish.Collapse)
        runCurrent()
        assertTrue(draft.expanded)
        assertNotNull(draft.error)
        assertEquals("http://bad.example/#fragment", draft.value.text)
        assertEquals(0, writes)
        assertEquals(AppSettings(), disk.value)
    }

    @Test
    fun blurBeforeDoneAlsoDeduplicatesAFailedWriteAndRetryKeepsDraft() = runTest {
        val disk = MutableStateFlow(AppSettings())
        var failing = true
        var writes = 0
        val session = SettingsSession(disk) { transform ->
            writes++
            if (failing) error("storage unavailable")
            disk.value = transform(disk.value)
        }
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val draft = edits.url()
        val input = TextFieldValue("https://changed.example/test", selection = TextRange(10))
        draft.open()
        edits.focus(draft, true)
        edits.input(draft, input)
        edits.focus(draft, false)
        runCurrent()
        edits.finish(draft, SettingFinish.Done)
        runCurrent()
        assertEquals(1, writes)
        assertSame(input, draft.value)
        assertTrue(draft.expanded)
        assertNotNull(draft.error)
        assertEquals(AppSettings(), disk.value)
        failing = false
        edits.finish(draft, SettingFinish.Retry)
        runCurrent()
        assertEquals(2, writes)
        assertEquals(input.text, disk.value.speed.latencyUrl)
        assertNull(draft.error)
    }

    @Test
    fun numericDraftsCommitTheCompletedIntegerAndDecimalOnlyAtBoundaries() = runTest {
        val disk = MutableStateFlow(AppSettings())
        var writes = 0
        val session = SettingsSession(disk) { transform -> writes++; disk.value = transform(disk.value) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val edits = SettingsEdits(session, backgroundScope)
        val count = edits.draft(SettingsPage.Speed, "count", { it.speed.latencyConcurrency.toString() },
            { validateIntegerInput(it, 1..32) }) { current, text ->
            current.copy(speed = current.speed.copy(latencyConcurrency = text.toInt()))
        }
        count.open()
        edits.focus(count, true)
        edits.input(count, TextFieldValue("1"))
        edits.input(count, TextFieldValue("12"))
        runCurrent()
        assertEquals(0, writes)
        edits.focus(count, false)
        runCurrent()
        assertEquals(12, disk.value.speed.latencyConcurrency)
        val decimal = edits.draft(SettingsPage.Speed, "decimal", { it.speed.minDownloadMiBps.toString() },
            ::validateDecimalInput) { current, text -> current.copy(speed = current.speed.copy(minDownloadMiBps = text.toDouble())) }
        decimal.open()
        edits.input(decimal, TextFieldValue("0.125"))
        edits.finish(decimal, SettingFinish.Collapse)
        runCurrent()
        assertEquals(0.125, disk.value.speed.minDownloadMiBps)
        assertFalse(decimal.expanded)
        assertEquals(2, writes)
    }

    @Test
    fun numericValidationRejectsIncompleteOverflowAndNonFiniteInput() {
        for (value in listOf("", "-", "0", "33", "2147483648", "3.5")) {
            assertNotNull(validateIntegerInput(value, 1..32), value)
        }
        for (value in listOf("1", "32")) assertNull(validateIntegerInput(value, 1..32))
        for (value in listOf("", "-", ".", "NaN", "Infinity", "1e999", "-0.1", "10000.1")) {
            assertNotNull(validateDecimalInput(value), value)
        }
        for (value in listOf("0", "0.125", "10000")) assertNull(validateDecimalInput(value))
    }
}

internal fun SettingsEdits.url(): SettingDraftState = draft(SettingsPage.Speed, "url", { it.speed.latencyUrl }, ::validateTargetUrl) {
    current, text -> current.copy(speed = current.speed.copy(latencyUrl = text))
}

internal fun SettingsEdits.agent(): SettingDraftState = draft(SettingsPage.Subscription, "agent", { it.subscription.userAgent }, ::validateUserAgent) {
    current, text -> current.copy(subscription = current.subscription.copy(userAgent = text))
}
