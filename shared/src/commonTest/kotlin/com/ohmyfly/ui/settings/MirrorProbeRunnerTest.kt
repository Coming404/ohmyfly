package com.ohmyfly.ui.settings

import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.mirror.MirrorProbe
import com.ohmyfly.mirror.MirrorHeadResponse
import com.ohmyfly.mirror.MirrorTransport
import com.ohmyfly.mirror.ProbeResponse
import com.ohmyfly.mirror.ProbeStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MirrorProbeRunnerTest {
    private fun entry(id: String) = MirrorEntry(id, id.uppercase(), "https://$id.example/")

    /** A slow host never answers before the test stops. */
    private fun transport(slow: Set<String> = emptySet()): MirrorTransport = headTransport { url, _ ->
        val host = url.substringAfter("://").substringBefore('/')
        if (slow.any { host.startsWith(it) }) delay(10_000)
        MirrorHeadResponse(200, 40)
    }

    private fun headTransport(block: suspend (String, Long) -> MirrorHeadResponse): MirrorTransport = object : MirrorTransport {
        override suspend fun head(url: String, timeoutMillis: Long): MirrorHeadResponse = block(url, timeoutMillis)
        override suspend fun fetch(url: String, timeoutMillis: Long, maxBytes: Int): ProbeResponse =
            error("Batch probes must not download bodies")
    }

    @Test
    fun batchRunsAtMostFiveHeadsAndProcessesQueuedTargetsOnce() = runTest {
        var active = 0
        var peak = 0
        var calls = 0
        val transport = headTransport { _, _ ->
            calls++
            active++
            peak = maxOf(peak, active)
            try {
                delay(100)
                MirrorHeadResponse(200, 20)
            } finally { active-- }
        }
        val runner = MirrorProbeRunner(MirrorProbe(transport), backgroundScope)
        val entries = (1..12).map { entry("m$it") }
        runner.measureAll(entries.map { it.id to it })
        runCurrent()
        assertEquals(5, active)
        assertEquals(7, entries.count { runner.phase(it.id) == MirrorTestPhase.Queued })
        assertEquals(0, runner.completed)

        advanceTimeBy(100)
        runCurrent()
        assertEquals(5, runner.completed)
        assertEquals(5, active)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(5, peak)
        assertEquals(12, calls)
        assertEquals(12, runner.completed)
        assertEquals(0, active)
        assertFalse(runner.running)
    }

    @Test
    fun stopKeepsFinishedRowsAndMarksOnlyTheUnfinishedOnes() = runTest {
        val runner = MirrorProbeRunner(MirrorProbe(transport(slow = setOf("b.example", "c.example"))), backgroundScope)
        runner.measureAll(listOf("direct" to null, "b" to entry("b"), "c" to entry("c")))
        runCurrent()
        // Five permits: the direct probe finishes at once, the two slow ones stay in flight.
        assertEquals(1, runner.completed)
        assertEquals(ProbeStatus.Success, runner.result(DIRECT_MIRROR_KEY)?.status)
        assertEquals(MirrorTestPhase.Running, runner.phase("b"))

        runner.stop()
        assertTrue(runner.stopping)
        assertEquals(MirrorTestPhase.Stopped, runner.phase("b"))
        assertEquals(MirrorTestPhase.Stopped, runner.phase("c"))
        assertNotEquals(MirrorTestPhase.Stopped, runner.phase(DIRECT_MIRROR_KEY))
        assertNotNull(runner.result(DIRECT_MIRROR_KEY))

        runCurrent()
        assertFalse(runner.stopping)
        assertFalse(runner.running)
    }

    @Test
    fun aNewBatchCannotStartWhileTheOldOneIsStillStopping() = runTest {
        val runner = MirrorProbeRunner(MirrorProbe(transport(slow = setOf("b.example"))), backgroundScope)
        runner.measureAll(listOf("b" to entry("b")))
        runCurrent()
        assertEquals(MirrorTestPhase.Running, runner.phase("b"))

        runner.stop()
        assertTrue(runner.running, "running stays true until the cancelled job really finished")
        runner.measureAll(listOf("direct" to null))
        assertTrue(runner.running)
        assertNull(runner.result(DIRECT_MIRROR_KEY))

        runCurrent()
        assertFalse(runner.running)
        runner.measureAll(listOf("direct" to null))
        assertTrue(runner.running)
        runCurrent()
        assertEquals(ProbeStatus.Success, runner.result(DIRECT_MIRROR_KEY)?.status)
    }

    @Test
    fun resultsFromAStoppedRunNeverOverwriteTheNextRun() = runTest {
        var generation = 0
        val transport = headTransport { _, _ ->
            val run = generation
            delay(if (run == 0) 5_000 else 10)
            MirrorHeadResponse(200, if (run == 0) 999 else 30)
        }
        val runner = MirrorProbeRunner(MirrorProbe(transport), backgroundScope)
        runner.measureAll(listOf("direct" to null))
        runCurrent()
        runner.stop()
        runCurrent()

        generation = 1
        runner.measureAll(listOf("direct" to null))
        advanceTimeBy(100)
        runCurrent()
        assertEquals(30L, runner.result(DIRECT_MIRROR_KEY)?.latencyMs)
    }

    @Test
    fun fullBatchCanAutoSelectFastestExactlyOnce() = runTest {
        val transport = headTransport { url, _ ->
            if ("b.example" in url) delay(100)
            MirrorHeadResponse(200, if ("b.example" in url) 10 else 80)
        }
        val runner = MirrorProbeRunner(MirrorProbe(transport), backgroundScope)
        runner.measureAll(listOf("a" to entry("a"), "b" to entry("b")))
        runCurrent()
        assertEquals(1, runner.completed)
        assertNull(runner.consumeFastestMirrorId())
        advanceTimeBy(100)
        runCurrent()

        assertEquals("b", runner.consumeFastestMirrorId())
        assertNull(runner.consumeFastestMirrorId())
    }

    @Test
    fun syncMirrorsKeepsLatencyOnRenameAndInvalidatesChangedTarget() = runTest {
        val mirror = entry("a")
        val runner = MirrorProbeRunner(MirrorProbe(transport()), backgroundScope)
        runner.syncMirrors(listOf(mirror))
        runner.measure(mirror.id, mirror)
        runCurrent()
        assertNotNull(runner.result(mirror.id))

        runner.syncMirrors(listOf(mirror.copy(name = "Renamed")))
        assertNotNull(runner.result(mirror.id))

        runner.syncMirrors(listOf(mirror.copy(address = "https://changed.example/")))
        assertNull(runner.result(mirror.id))
    }

    @Test
    fun singleProbeNeverTriggersBatchAutoSelection() = runTest {
        val runner = MirrorProbeRunner(MirrorProbe(transport()), backgroundScope)
        runner.measure("a", entry("a"))
        runCurrent()

        assertEquals(ProbeStatus.Success, runner.result("a")?.status)
        assertNull(runner.consumeFastestMirrorId())
    }

    @Test
    fun allFailedBatchDoesNotAutoSelect() = runTest {
        val transport = headTransport { _, _ -> MirrorHeadResponse(503, 12) }
        val runner = MirrorProbeRunner(MirrorProbe(transport), backgroundScope)
        runner.measureAll(listOf("a" to entry("a")))
        runCurrent()

        assertEquals(ProbeStatus.Failed, runner.result("a")?.status)
        assertNull(runner.consumeFastestMirrorId())
    }

    @Test
    fun aStoppedRunIsNotReportedAsAnError() = runTest {
        val runner = MirrorProbeRunner(MirrorProbe(transport(slow = setOf("b.example"))), backgroundScope)
        runner.measureAll(listOf("b" to entry("b")))
        runCurrent()
        runner.stop()
        assertNull(runner.result("b"))
        assertEquals(MirrorTone.Muted, mirrorTone(runner.result("b"), runner.phase("b")))
        runCurrent()
        assertTrue(runner.stopped)
        assertNull(runner.consumeFastestMirrorId())
    }

}
