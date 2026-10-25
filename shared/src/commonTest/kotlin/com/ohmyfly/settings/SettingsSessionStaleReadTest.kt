package com.ohmyfly.settings

import com.ohmyfly.ui.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSessionStaleReadTest {
    /**
     * Regression: cancellation is cooperative, so a collector being cancelled by change() can
     * still deliver one more stored value. That read must not clobber the optimistic snapshot.
     */
    @Test
    fun staleEmissionDuringCancellationKeepsTheOptimisticValue() = runTest {
        val values = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        val session = SettingsSession(values) { transform ->
            gate.await()
            values.value = transform(values.value)
        }
        backgroundScope.launch { session.run() }
        runCurrent()

        session.change { it.copy(themeMode = ThemeMode.Dark) }
        // Emitted while the first change is still queued: this is the stale value the fix blocks.
        values.value = values.value.copy(themeMode = ThemeMode.Light)
        runCurrent()
        assertEquals(ThemeMode.Dark, session.state.settings.themeMode)

        gate.complete(Unit)
        runCurrent()
        assertEquals(ThemeMode.Dark, session.state.settings.themeMode)
        assertEquals(ThemeMode.Dark, values.value.themeMode)
    }
}
