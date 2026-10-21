package dev.ohmydrift.ui.settings

import androidx.compose.ui.text.input.TextFieldValue
import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.MirrorMode
import dev.ohmydrift.mirror.MirrorOrder
import dev.ohmydrift.settings.AppSettings
import dev.ohmydrift.settings.SettingsSession
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
class MirrorManualSaveTest {
    private val entry = MirrorEntry("a", "A", "https://a.example/")

    @Test
    fun closingUnsubmittedInputKeepsOneDraftAndNeverWrites() = runTest {
        val disk = MutableStateFlow(AppSettings(mirrors = emptyList()))
        var writes = 0
        val session = SettingsSession(disk) { transform -> writes++; disk.value = transform(disk.value) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val owner = MirrorPageState(session, backgroundScope)
        val draft = owner.draft(null)
        draft.name = TextFieldValue("新增")
        draft.address = TextFieldValue("https://new.example/")
        owner.show(MirrorPageState.Panel.Edit(draft))
        owner.dismiss()
        owner.finishDismiss()
        runCurrent()
        assertEquals(0, writes)
        assertTrue(disk.value.mirrors.isEmpty())
        assertSame(draft, owner.draft(null))
        assertEquals("新增", draft.name.text)
    }

    @Test
    fun explicitSaveWaitsForRealConfirmationAndDoesNotAutoSelect() = runTest {
        val disk = MutableStateFlow(AppSettings(mirrors = emptyList()))
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val session = SettingsSession(disk) { transform -> writes++; gate.await(); disk.value = transform(disk.value) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val owner = MirrorPageState(session, backgroundScope)
        val draft = owner.draft(null)
        draft.name = TextFieldValue("新增")
        draft.address = TextFieldValue("https://new.example/")
        owner.show(MirrorPageState.Panel.Edit(draft))
        owner.submit(draft)
        owner.submit(draft)
        runCurrent()
        assertTrue(owner.visible)
        assertNull(draft.confirmed)
        assertEquals(1, writes)
        assertEquals(1, session.state.settings!!.mirrors.size)
        assertTrue(session.state.confirmedSettings!!.mirrors.isEmpty())
        gate.complete(Unit)
        runCurrent()
        assertFalse(owner.visible)
        assertNotNull(draft.confirmed)
        assertEquals(1, disk.value.mirrors.size)
        assertNull(disk.value.selectedMirrorId)
    }

    @Test
    fun invalidSaveAndFailedSaveBothKeepInputAndDialog() = runTest {
        val disk = MutableStateFlow(AppSettings(mirrors = listOf(entry)))
        var writes = 0
        val session = SettingsSession(disk) { writes++; error("storage unavailable") }
        backgroundScope.launch { session.run() }
        runCurrent()
        val owner = MirrorPageState(session, backgroundScope)
        val draft = owner.draft(entry)
        owner.show(MirrorPageState.Panel.Edit(draft))
        draft.address = TextFieldValue("ftp://a.example/")
        owner.submit(draft)
        runCurrent()
        assertEquals(0, writes)
        assertTrue(draft.attempted)
        assertTrue(owner.visible)
        draft.address = TextFieldValue("https://changed.example/")
        owner.submit(draft)
        runCurrent()
        assertEquals(1, writes)
        assertEquals(entry, disk.value.mirrors.single())
        assertEquals("https://changed.example/", draft.address.text)
        assertNotNull(draft.failure)
        assertTrue(owner.visible)
        owner.submit(draft)
        runCurrent()
        assertEquals(2, writes)
    }

    @Test
    fun legacyTemplateStaysTemplateUntilExplicitSelection() {
        val template = entry.copy(mode = MirrorMode.Template, address = "https://a.example/fetch?url={url}")
        val draft = MirrorDraftState(template.id, template)
        assertEquals(MirrorMode.Template, draft.candidate.mode)
        assertNull(mirrorDraftError(draft.candidate, listOf(template)))
        draft.mode = MirrorMode.Prefix
        assertNotNull(mirrorDraftError(draft.candidate, listOf(template)))
        assertEquals(template, draft.confirmed)
    }

    @Test
    fun deletingLastMirrorDisablesFeatureAndFailureDoesNotEmptyList() = runTest {
        val disk = MutableStateFlow(AppSettings(mirrors = listOf(entry), selectedMirrorId = entry.id, mirrorEnabled = true))
        var fail = true
        val session = SettingsSession(disk) { transform ->
            if (fail) error("storage unavailable")
            disk.value = transform(disk.value)
        }
        backgroundScope.launch { session.run() }
        runCurrent()
        val owner = MirrorPageState(session, backgroundScope)
        owner.show(MirrorPageState.Panel.Delete(entry.id))
        owner.delete(entry.id)
        runCurrent()
        assertEquals(listOf(entry), disk.value.mirrors)
        assertTrue(disk.value.mirrorEnabled)
        assertTrue(owner.visible)
        fail = false
        owner.delete(entry.id)
        runCurrent()
        assertTrue(disk.value.mirrors.isEmpty())
        assertFalse(disk.value.mirrorEnabled)
        assertNull(disk.value.selectedMirrorId)
        assertFalse(owner.visible)
    }

    @Test
    fun bareDomainIsNormalizedOnlyWhenUserSaves() = runTest {
        val disk = MutableStateFlow(AppSettings(mirrors = emptyList(), mirrorEnabled = false))
        val session = SettingsSession(disk) { transform -> disk.value = transform(disk.value) }
        backgroundScope.launch { session.run() }
        runCurrent()
        val owner = MirrorPageState(session, backgroundScope)
        val draft = owner.draft(null)
        draft.name = TextFieldValue("A")
        draft.address = TextFieldValue("www.Example.com/Base")
        runCurrent()
        assertTrue(disk.value.mirrors.isEmpty())
        owner.submit(draft)
        runCurrent()
        assertEquals("https://www.example.com/Base/", disk.value.mirrors.single().address)
        assertFalse(disk.value.mirrorEnabled)
    }

    @Test
    fun sortButtonCyclesWithoutChangingDefaultOrPersistedPriority() {
        assertEquals(MirrorOrder.LatencyAscending, nextDisplayOrder(MirrorOrder.Manual))
        assertEquals(MirrorOrder.LatencyDescending, nextDisplayOrder(MirrorOrder.LatencyAscending))
        assertEquals(MirrorOrder.Manual, nextDisplayOrder(MirrorOrder.LatencyDescending))
        assertEquals(MirrorOrder.LatencyAscending, AppSettings().mirrorOrder)
    }
}
