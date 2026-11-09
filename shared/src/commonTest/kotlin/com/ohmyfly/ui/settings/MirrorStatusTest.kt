package com.ohmyfly.ui.settings

import com.ohmyfly.mirror.MirrorProbeResult
import com.ohmyfly.mirror.ProbeStatus
import kotlin.test.*

class MirrorStatusTest {
    private fun result(status: ProbeStatus, latencyMs: Long? = null, detail: String = "") =
        MirrorProbeResult(status, latencyMs, detail)

    @Test
    fun timeoutIsNeverShownAsZeroMilliseconds() {
        val timeout = result(ProbeStatus.Timeout, 0)
        val short = mirrorStatusText(timeout, MirrorTestPhase.Idle)
        assertTrue("0 ms" !in short)
        assertTrue(short.contains("超时"))
        assertTrue("0 ms" !in mirrorStatusDetail(timeout, MirrorTestPhase.Idle))
    }

    @Test
    fun successShowsSingleHttpResponseTime() {
        val passed = result(ProbeStatus.Success, 240, "HTTP 302")
        assertTrue(mirrorStatusText(passed, MirrorTestPhase.Idle).contains("240 ms"))
        assertEquals("HTTP 响应 240 ms，HTTP 302", mirrorStatusDetail(passed, MirrorTestPhase.Idle))
        assertEquals(MirrorTone.Normal, mirrorTone(passed, MirrorTestPhase.Idle))
    }

    @Test
    fun shortStatusStaysOnOneLine() {
        assertEquals("未测试", mirrorStatusText(null, MirrorTestPhase.Idle))
        assertEquals("不可用", mirrorStatusText(result(ProbeStatus.Failed, detail = "请求失败"), MirrorTestPhase.Idle))
        assertEquals("请求超时", mirrorStatusText(result(ProbeStatus.Timeout, 0), MirrorTestPhase.Idle))
        assertEquals("已停止", mirrorStatusText(null, MirrorTestPhase.Stopped))
    }

    @Test
    fun queuedAndRunningOutrankOldResults() {
        val stale = result(ProbeStatus.Success, 99)
        assertEquals("排队中", mirrorStatusText(stale, MirrorTestPhase.Queued))
        assertEquals("测试中…", mirrorStatusText(stale, MirrorTestPhase.Running))
        assertEquals("正在等待 HTTP 响应头", mirrorStatusDetail(stale, MirrorTestPhase.Running))
        assertEquals(MirrorTone.Active, mirrorTone(stale, MirrorTestPhase.Running))
        assertNotEquals(MirrorTone.Error, mirrorTone(stale, MirrorTestPhase.Running))
    }

    @Test
    fun untestedAndFailuresAreDistinct() {
        assertEquals(MirrorTone.Muted, mirrorTone(null, MirrorTestPhase.Idle))
        assertEquals(MirrorTone.Error, mirrorTone(result(ProbeStatus.Failed), MirrorTestPhase.Idle))
        assertEquals(MirrorTone.Error, mirrorTone(result(ProbeStatus.Timeout), MirrorTestPhase.Idle))
        assertEquals(MirrorTone.Muted, mirrorTone(null, MirrorTestPhase.Stopped))
    }

    @Test
    fun latencyBandsUseStableThresholds() {
        assertEquals(MirrorLatencyBand.Fast, mirrorLatencyBand(100))
        assertEquals(MirrorLatencyBand.Moderate, mirrorLatencyBand(101))
        assertEquals(MirrorLatencyBand.Moderate, mirrorLatencyBand(300))
        assertEquals(MirrorLatencyBand.Slow, mirrorLatencyBand(301))
    }

    @Test
    fun detailKeepsTheRealReasonForTheReader() {
        val failed = result(ProbeStatus.Failed, detail = "HTTP 503")
        assertEquals("HTTP 503", mirrorStatusDetail(failed, MirrorTestPhase.Idle))
        assertEquals("请求超时", mirrorStatusDetail(result(ProbeStatus.Timeout, 0), MirrorTestPhase.Idle))
        assertEquals("尚未测试", mirrorStatusDetail(null, MirrorTestPhase.Idle))
    }
}
