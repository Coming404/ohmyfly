package dev.ohmydrift.ui.settings

import dev.ohmydrift.mirror.MirrorProbeResult
import dev.ohmydrift.mirror.ProbeStatus
import kotlin.test.*

class MirrorStatusTest {
    private fun result(status: ProbeStatus, latencyMs: Long? = null, successes: Int = 1) =
        MirrorProbeResult(status, latencyMs, successes, "")

    @Test
    fun timeoutIsNeverShownAsZeroMilliseconds() {
        val text = mirrorStatusText(result(ProbeStatus.Timeout, 0, 0), MirrorTestPhase.Idle)
        assertTrue("0 ms" !in text)
        assertTrue(text.contains("超时"))
    }

    @Test
    fun successShowsMeasuredFirstByte() {
        val text = mirrorStatusText(result(ProbeStatus.Success, 240), MirrorTestPhase.Idle)
        assertTrue(text.contains("240 ms"))
        assertTrue(text.contains("成功 1 次"))
        assertEquals(MirrorTone.Active, mirrorTone(result(ProbeStatus.Success, 240), MirrorTestPhase.Idle))
    }

    @Test
    fun queuedAndRunningOutrankOldResults() {
        val stale = result(ProbeStatus.Success, 99)
        assertEquals("排队中", mirrorStatusText(stale, MirrorTestPhase.Queued))
        assertEquals("测试中…", mirrorStatusText(stale, MirrorTestPhase.Running))
        assertNotEquals(MirrorTone.Error, mirrorTone(stale, MirrorTestPhase.Running))
    }

    @Test
    fun untestedAndFailuresAreDistinct() {
        assertEquals(MirrorTone.Normal, mirrorTone(null, MirrorTestPhase.Idle))
        assertEquals(MirrorTone.Error, mirrorTone(result(ProbeStatus.Failed), MirrorTestPhase.Idle))
        assertEquals(MirrorTone.Error, mirrorTone(result(ProbeStatus.InvalidContent), MirrorTestPhase.Idle))
        assertEquals("未测试", mirrorStatusText(null, MirrorTestPhase.Idle))
    }
}
