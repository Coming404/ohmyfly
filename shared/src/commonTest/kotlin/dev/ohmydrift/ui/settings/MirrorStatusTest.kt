package dev.ohmydrift.ui.settings

import dev.ohmydrift.mirror.MirrorProbeResult
import dev.ohmydrift.mirror.ProbeStatus
import kotlin.test.*

class MirrorStatusTest {
    private fun result(status: ProbeStatus, latencyMs: Long? = null, successes: Int = 1, detail: String = "") =
        MirrorProbeResult(status, latencyMs, successes, detail)

    @Test
    fun timeoutIsNeverShownAsZeroMilliseconds() {
        val timeout = result(ProbeStatus.Timeout, 0, 0)
        val short = mirrorStatusText(timeout, MirrorTestPhase.Idle)
        assertTrue("0 ms" !in short)
        assertTrue(short.contains("超时"))
        assertTrue("0 ms" !in mirrorStatusDetail(timeout, MirrorTestPhase.Idle))
    }

    @Test
    fun successShowsMeasuredFirstByte() {
        val passed = result(ProbeStatus.Success, 240)
        assertTrue(mirrorStatusText(passed, MirrorTestPhase.Idle).contains("240 ms"))
        assertTrue(mirrorStatusDetail(passed, MirrorTestPhase.Idle).contains("成功 1 次"))
        assertEquals(MirrorTone.Normal, mirrorTone(passed, MirrorTestPhase.Idle))
    }

    @Test
    fun shortStatusStaysOnOneLine() {
        assertEquals("未测试", mirrorStatusText(null, MirrorTestPhase.Idle))
        assertEquals("不可用", mirrorStatusText(result(ProbeStatus.Failed, detail = "请求失败"), MirrorTestPhase.Idle))
        assertEquals("请求超时", mirrorStatusText(result(ProbeStatus.Timeout, 0, 0), MirrorTestPhase.Idle))
        assertEquals("已停止", mirrorStatusText(null, MirrorTestPhase.Stopped))
    }

    @Test
    fun queuedAndRunningOutrankOldResults() {
        val stale = result(ProbeStatus.Success, 99)
        assertEquals("排队中", mirrorStatusText(stale, MirrorTestPhase.Queued))
        assertEquals("测试中…", mirrorStatusText(stale, MirrorTestPhase.Running))
        assertEquals(MirrorTone.Active, mirrorTone(stale, MirrorTestPhase.Running))
        assertNotEquals(MirrorTone.Error, mirrorTone(stale, MirrorTestPhase.Running))
    }

    @Test
    fun untestedAndFailuresAreDistinct() {
        assertEquals(MirrorTone.Muted, mirrorTone(null, MirrorTestPhase.Idle))
        assertEquals(MirrorTone.Error, mirrorTone(result(ProbeStatus.Failed), MirrorTestPhase.Idle))
        assertEquals(MirrorTone.Error, mirrorTone(result(ProbeStatus.InvalidContent), MirrorTestPhase.Idle))
        assertEquals(MirrorTone.Muted, mirrorTone(null, MirrorTestPhase.Stopped))
    }

    @Test
    fun detailKeepsTheRealReasonForTheReader() {
        val failed = result(ProbeStatus.Failed, detail = "返回内容与固定样本不一致")
        assertEquals("返回内容与固定样本不一致", mirrorStatusDetail(failed, MirrorTestPhase.Idle))
        assertEquals("请求超时", mirrorStatusDetail(result(ProbeStatus.Timeout, 0, 0), MirrorTestPhase.Idle))
        assertEquals("尚未测试", mirrorStatusDetail(null, MirrorTestPhase.Idle))
    }
}
