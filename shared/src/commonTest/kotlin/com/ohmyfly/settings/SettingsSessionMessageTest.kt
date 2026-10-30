package com.ohmyfly.settings

import com.ohmyfly.ui.AppMessageKind
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSessionMessageTest {
    @Test
    fun successWaitsForBothTransactionAndReadBack() = runTest {
        var disk = AppSettings()
        var reads = 0
        val writeGate = CompletableDeferred<Unit>()
        val readGate = CompletableDeferred<Unit>()
        val notices = mutableListOf<Pair<AppMessageKind, String>>()
        val values = flow {
            if (++reads > 1) readGate.await()
            emit(disk)
            awaitCancellation()
        }
        val session = SettingsSession(values) { transform ->
            writeGate.await()
            disk = transform(disk)
        }
        session.onMessage = { kind, text -> notices += kind to text }
        backgroundScope.launch { session.run() }
        runCurrent()
        session.change("明暗模式") { it.copy(themeMode = ThemeMode.Dark) }
        runCurrent()
        assertTrue(notices.isEmpty())
        writeGate.complete(Unit)
        runCurrent()
        assertEquals(ThemeMode.Dark, disk.themeMode)
        assertTrue(notices.isEmpty())
        assertEquals(1, session.state.pendingWrites)
        readGate.complete(Unit)
        runCurrent()
        assertEquals(listOf(AppMessageKind.Success to "明暗模式已保存"), notices)
    }

    @Test
    fun rollbackReportsOnlyFailedItemOnceWithNamedOrGenericText() = runTest {
        for ((name, inlineError) in listOf("并发数" to false, null to false, "并发数" to true)) {
            val disk = MutableStateFlow(AppSettings())
            val gate = CompletableDeferred<Unit>()
            val notices = mutableListOf<Pair<AppMessageKind, String>>()
            var writes = 0
            val session = SettingsSession(disk) {
                writes++
                gate.await()
                error("storage unavailable")
            }
            session.onMessage = { kind, text -> notices += kind to text }
            backgroundScope.launch { session.run() }
            runCurrent()
            val transform: (AppSettings) -> AppSettings = { it.copy(speed = it.speed.copy(latencyConcurrency = 4)) }
            if (inlineError) {
                backgroundScope.launch { session.changeConfirmed(name, reportErrors = false, transform = transform) }
            } else session.change(name, transform)
            runCurrent()
            if (name != null) session.change("减少动态效果") { it.copy(reduceMotion = true) }
            runCurrent()
            assertTrue(notices.isEmpty())
            gate.complete(Unit)
            runCurrent()
            val text = when {
                inlineError -> "因并发数未保存，后续未保存的修改也已撤回"
                name == null -> "设置保存失败，已恢复原值"
                else -> "并发数保存失败，已恢复原值；后续未保存的修改也已撤回"
            }
            assertEquals(listOf(AppMessageKind.Failure to text), notices)
            assertEquals(1, writes)
            assertEquals(disk.value, session.state.settings)
            assertEquals(0, session.state.pendingWrites)
        }
    }

    @Test
    fun invalidInputReportsNamedOrGenericErrorWithoutWriting() = runTest {
        val disk = MutableStateFlow(AppSettings())
        var writes = 0
        val notices = mutableListOf<Pair<AppMessageKind, String>>()
        val session = SettingsSession(disk) { writes++ }
        session.onMessage = { kind, text -> notices += kind to text }
        backgroundScope.launch { session.run() }
        runCurrent()
        val invalid: (AppSettings) -> AppSettings = { it.copy(speed = it.speed.copy(latencyConcurrency = 0)) }
        val reason = validateSettings(invalid(disk.value))
        assertIs<SettingsWriteResult.Rejected>(session.changeConfirmed("并发数", transform = invalid))
        session.change(invalid)
        assertEquals(listOf(AppMessageKind.Error to "并发数无效：$reason", AppMessageKind.Error to "设置无效：$reason"), notices)
        assertEquals(0, writes)
        assertEquals(disk.value, session.state.settings)
    }

    @Test
    fun inlineErrorsStayLocalButConfirmedWritesStillReportSuccess() = runTest {
        val disk = MutableStateFlow(AppSettings())
        var fail = true
        val notices = mutableListOf<Pair<AppMessageKind, String>>()
        val session = SettingsSession(disk) { transform ->
            if (fail) error("storage unavailable")
            disk.value = transform(disk.value)
        }
        session.onMessage = { kind, text -> notices += kind to text }
        backgroundScope.launch { session.run() }
        runCurrent()
        assertIs<SettingsWriteResult.Rejected>(session.changeConfirmed("并发数", reportErrors = false) {
            it.copy(speed = it.speed.copy(latencyConcurrency = 0))
        })
        assertIs<SettingsWriteResult.Failed>(session.changeConfirmed("并发数", reportErrors = false) {
            it.copy(speed = it.speed.copy(latencyConcurrency = 4))
        })
        assertTrue(notices.isEmpty())
        fail = false
        val transform: (AppSettings) -> AppSettings = { it.copy(speed = it.speed.copy(latencyConcurrency = 4)) }
        assertIs<SettingsWriteResult.Confirmed>(session.changeConfirmed(transform))
        assertEquals(listOf(AppMessageKind.Success to "设置已保存"), notices)
    }

    @Test
    fun rejectedOperationReportsErrorButReadFailureRemainsDialogOnly() = runTest {
        val notices = mutableListOf<Pair<AppMessageKind, String>>()
        var writes = 0
        val session = SettingsSession(flow<AppSettings> { error("read failed") }) { writes++ }
        session.onMessage = { kind, text -> notices += kind to text }
        assertIs<SettingsWriteResult.Rejected>(session.changeConfirmed("并发数") { it })
        assertEquals(listOf(AppMessageKind.Error to "并发数暂时无法保存，请稍后重试"), notices)
        notices.clear()
        backgroundScope.launch { session.run() }
        runCurrent()
        assertTrue(session.state.readFailed)
        assertIs<SettingsWriteResult.Rejected>(session.changeConfirmed { it })
        session.change { it }
        assertTrue(notices.isEmpty())
        assertEquals(0, writes)
    }
}
