package com.ohmyfly.ui.settings

import androidx.compose.ui.text.input.TextFieldValue
import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.ui.AppMessageKind
import com.ohmyfly.ui.AppMessageState
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
        val messages = AppMessageState()
        session.onMessage = messages::show
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
        assertNull(messages.current)
    }

    @Test
    fun explicitSaveWaitsForRealConfirmationAndDoesNotAutoSelect() = runTest {
        val disk = MutableStateFlow(AppSettings(mirrors = emptyList()))
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val session = SettingsSession(disk) { transform -> writes++; gate.await(); disk.value = transform(disk.value) }
        val messages = AppMessageState()
        session.onMessage = messages::show
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
        assertEquals(1, session.state.settings.mirrors.size)
        assertTrue(session.state.confirmedSettings!!.mirrors.isEmpty())
        assertNull(messages.current)
        gate.complete(Unit)
        runCurrent()
        assertFalse(owner.visible)
        assertNotNull(draft.confirmed)
        assertEquals(1, disk.value.mirrors.size)
        assertNull(disk.value.selectedMirrorId)
        assertEquals(AppMessageKind.Success, messages.current?.kind)
        assertEquals("镜像已保存", messages.current?.text)
    }

    @Test
    fun invalidSaveAndFailedSaveBothKeepInputAndDialog() = runTest {
        val disk = MutableStateFlow(AppSettings(mirrors = listOf(entry)))
        var writes = 0
        val session = SettingsSession(disk) { writes++; error("storage unavailable") }
        val messages = AppMessageState()
        session.onMessage = messages::show
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
        assertNull(messages.current)
    }

    @Test
    fun deletingLastMirrorDisablesFeatureAndFailureDoesNotEmptyList() = runTest {
        val disk = MutableStateFlow(AppSettings(mirrors = listOf(entry), selectedMirrorId = entry.id, mirrorEnabled = true))
        var fail = true
        val session = SettingsSession(disk) { transform ->
            if (fail) error("storage unavailable")
            disk.value = transform(disk.value)
        }
        val messages = AppMessageState()
        session.onMessage = messages::show
        backgroundScope.launch { session.run() }
        runCurrent()
        val owner = MirrorPageState(session, backgroundScope)
        owner.show(MirrorPageState.Panel.Delete(entry.id))
        owner.delete(entry.id)
        runCurrent()
        assertEquals(listOf(entry), disk.value.mirrors)
        assertTrue(disk.value.mirrorEnabled)
        assertTrue(owner.visible)
        assertNull(messages.current)
        fail = false
        owner.delete(entry.id)
        runCurrent()
        assertTrue(disk.value.mirrors.isEmpty())
        assertFalse(disk.value.mirrorEnabled)
        assertNull(disk.value.selectedMirrorId)
        assertFalse(owner.visible)
        assertEquals("镜像已删除", messages.current?.text)
    }

    @Test
    fun bareDomainIsNormalizedOnlyWhenUserSaves() = runTest {
        val disk = MutableStateFlow(AppSettings(mirrors = emptyList(), mirrorEnabled = false))
        val session = SettingsSession(disk) { transform -> disk.value = transform(disk.value) }
        val messages = AppMessageState()
        session.onMessage = messages::show
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
        assertEquals("镜像已保存", messages.current?.text)
    }
}
