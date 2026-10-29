package com.ohmyfly.settings

import com.ohmyfly.ui.AppMessageKind
import com.ohmyfly.ui.AppMessageState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MotionSettingsSessionTest {
    @Test
    fun motionPreferenceIsImmediateAndRollsBackAfterFailedSave() = runTest {
        val values = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        var fail = true
        val session = SettingsSession(values) { transform ->
            gate.await()
            if (fail) error("storage unavailable")
            values.value = transform(values.value)
        }
        val messages = AppMessageState()
        session.onMessage = messages::show
        backgroundScope.launch { session.run() }
        runCurrent()
        session.change { it.copy(reduceMotion = true) }
        assertTrue(session.state.settings.reduceMotion)
        assertFalse(values.value.reduceMotion)
        gate.complete(Unit)
        runCurrent()
        assertFalse(session.state.settings.reduceMotion)
        assertEquals(AppMessageKind.Failure, messages.current?.kind)
        fail = false
        session.change { it.copy(reduceMotion = true) }
        assertTrue(session.state.settings.reduceMotion)
        runCurrent()
        assertTrue(values.value.reduceMotion)
    }
}
