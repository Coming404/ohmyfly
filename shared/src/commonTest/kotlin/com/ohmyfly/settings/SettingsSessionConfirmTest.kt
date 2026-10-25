package com.ohmyfly.settings

import com.ohmyfly.ui.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
import kotlin.test.assertTrue

/** 真实写入确认：乐观值不等于确认、失败后缀、写后读取失败、重读与会话结束。 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSessionConfirmTest {
    @Test
    fun optimisticValueIsPublishedButNotReportedAsConfirmed() = runTest {
        val values = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        val session = SettingsSession(values) { transform ->
            gate.await()
            values.value = transform(values.value)
        }
        backgroundScope.launch { session.run() }
        runCurrent()

        val result = async { session.changeConfirmed { it.copy(themeMode = ThemeMode.Dark) } }
        runCurrent()
        // 乐观快照立刻可见，但它不是确认结果。
        assertEquals(ThemeMode.Dark, session.state.settings.themeMode)
        assertEquals(ThemeMode.System, session.state.confirmedSettings?.themeMode)
        assertEquals(1, session.state.pendingWrites)
        assertFalse(result.isCompleted)

        gate.complete(Unit)
        runCurrent()
        val settled = result.await()
        assertIs<SettingsWriteResult.Confirmed>(settled)
        assertEquals(ThemeMode.Dark, settled.settings.themeMode)
        assertEquals(settled.settings, session.state.settings)
        assertEquals(settled.settings, session.state.confirmedSettings)
        assertEquals(0, session.state.pendingWrites)
    }

    @Test
    fun storedEmissionWhilePendingNeverBecomesConfirmedSettings() = runTest {
        val values = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        val session = SettingsSession(values) { transform ->
            gate.await()
            values.value = transform(values.value)
        }
        backgroundScope.launch { session.run() }
        runCurrent()

        val result = async { session.changeConfirmed { it.copy(reduceMotion = true) } }
        runCurrent()
        // 其它来源写盘：不能当成这次操作的确认。
        values.value = values.value.copy(reduceTransparency = true)
        runCurrent()
        assertFalse(session.state.confirmedSettings?.reduceTransparency ?: false)
        assertFalse(session.state.confirmedSettings?.reduceMotion ?: false)
        assertEquals(1, session.state.pendingWrites)
        assertFalse(result.isCompleted)

        gate.complete(Unit)
        runCurrent()
        assertIs<SettingsWriteResult.Confirmed>(result.await())
        assertTrue(session.state.confirmedSettings?.reduceMotion ?: false)
    }

    @Test
    fun failedWriteFailsTheOperationAndItsWholeSuffix() = runTest {
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

        val first = async { session.changeConfirmed { it.copy(themeMode = ThemeMode.Dark) } }
        val second = async { session.changeConfirmed { it.copy(materialMode = MaterialMode.SoftGlass) } }
        val third = async { session.changeConfirmed { it.copy(reduceTransparency = true) } }
        runCurrent()
        assertEquals(3, session.state.pendingWrites)
        gate.complete(Unit)
        runCurrent()

        assertIs<SettingsWriteResult.Confirmed>(first.await())
        assertIs<SettingsWriteResult.Failed>(second.await())
        assertIs<SettingsWriteResult.Failed>(third.await())
        assertEquals(2, writes)
        // 已确认值仍在，失败后缀既不落盘也不冒充确认。
        assertEquals(ThemeMode.Dark, session.state.confirmedSettings?.themeMode)
        assertEquals(MaterialMode.LiquidGlass, session.state.settings.materialMode)
        assertEquals(false, session.state.settings.reduceTransparency)
        assertEquals(0, session.state.pendingWrites)
        assertTrue(session.state.error != null)
    }

    @Test
    fun rejectedTransformNeverEntersTheQueue() = runTest {
        val values = MutableStateFlow(AppSettings())
        var writes = 0
        val session = SettingsSession(values) { writes++ }
        backgroundScope.launch { session.run() }
        runCurrent()

        assertIs<SettingsWriteResult.Rejected>(
            session.changeConfirmed { it.copy(speed = it.speed.copy(downloadMiB = -1)) },
        )
        runCurrent()
        assertEquals(0, writes)
        assertEquals(0, session.state.pendingWrites)
        assertTrue(session.state.error != null)
    }

    @Test
    fun readAfterWriteFailureIsUncertainAndRetryReadRestoresWriting() = runTest {
        var committed = AppSettings()
        var subscriptions = 0
        var failConfirmation = true
        var writes = 0
        val values = flow {
            subscriptions++
            if (subscriptions > 1 && failConfirmation) error("confirmation read failed")
            emit(committed)
            awaitCancellation()
        }
        val session = SettingsSession(values) { transform ->
            writes++
            committed = transform(committed)
        }
        backgroundScope.launch { session.run() }
        runCurrent()

        val result = session.changeConfirmed { it.copy(themeMode = ThemeMode.Dark) }
        assertEquals(SettingsWriteResult.Uncertain, result)
        assertEquals(1, writes)
        assertTrue(session.state.readFailed)
        assertTrue(session.state.writeUncertain)
        assertEquals(0, session.state.pendingWrites)
        // 磁盘结果未知：不假装已确认，也不回滚成旧值。
        assertEquals(ThemeMode.Dark, session.state.settings.themeMode)
        assertEquals(ThemeMode.System, session.state.confirmedSettings?.themeMode)

        // 只读失败期间不接受新写入。
        assertIs<SettingsWriteResult.Rejected>(session.changeConfirmed { it.copy(reduceMotion = true) })
        assertEquals(1, writes)

        failConfirmation = false
        session.retryRead()
        runCurrent()
        assertFalse(session.state.readFailed)
        assertFalse(session.state.writeUncertain)
        assertEquals(ThemeMode.Dark, session.state.settings.themeMode)
        assertEquals(ThemeMode.Dark, session.state.confirmedSettings?.themeMode)

        val retried = session.changeConfirmed { it.copy(reduceMotion = true) }
        assertIs<SettingsWriteResult.Confirmed>(retried)
        assertEquals(2, writes)
        assertTrue(retried.settings.reduceMotion)
    }

    @Test
    fun retryReadNeverReplaysTheUnconfirmedOperation() = runTest {
        val initial = AppSettings()
        var subscriptions = 0
        var writes = 0
        val values = flow {
            subscriptions++
            if (subscriptions == 1) {
                emit(initial)
                awaitCancellation()
            }
            error("read failed")
        }
        val session = SettingsSession(values) { writes++ }
        backgroundScope.launch { session.run() }
        runCurrent()

        session.change { it.copy(themeMode = ThemeMode.Dark) }
        runCurrent()
        assertEquals(1, writes)
        assertTrue(session.state.readFailed)
        assertTrue(session.state.writeUncertain)

        session.retryRead()
        runCurrent()
        // 重读失败时既不重发写入，也不清掉界面草稿值。
        assertTrue(session.state.readFailed)
        assertTrue(session.state.writeUncertain)
        assertEquals(1, writes)
        assertEquals(ThemeMode.Dark, session.state.settings.themeMode)
        assertEquals(AppSettings(), session.state.confirmedSettings)
    }

    @Test
    fun sessionLifetimeEndCancelsPendingConfirmations() = runTest {
        val values = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        val session = SettingsSession(values) { transform ->
            gate.await()
            values.value = transform(values.value)
        }
        val job = backgroundScope.launch { session.run() }
        runCurrent()

        // 等待方不永远挂着，也不被伪造成保存失败。
        val pending = async { runCatching { session.changeConfirmed { it.copy(themeMode = ThemeMode.Dark) } } }
        runCurrent()
        job.cancel()
        runCurrent()

        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(AppSettings(), values.value)
    }
}
