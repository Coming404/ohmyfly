package dev.ohmydrift.ui.settings

import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.MirrorProbe
import dev.ohmydrift.mirror.MirrorSample
import dev.ohmydrift.mirror.MirrorTransport
import dev.ohmydrift.mirror.ProbeResponse
import dev.ohmydrift.mirror.ProbeStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MirrorProbeRunnerTest {
    private fun entry(id: String) = MirrorEntry(id, id.uppercase(), "https://$id.example/")

    /** Each probe takes three samples; a "slow" host never answers before the test stops. */
    private fun transport(slow: Set<String> = emptySet()): MirrorTransport = object : MirrorTransport {
        override suspend fun fetch(url: String, timeoutMillis: Long, maxBytes: Int): ProbeResponse {
            val host = url.substringAfter("://").substringBefore('/')
            if (slow.any { host.startsWith(it) }) delay(10_000)
            return ProbeResponse(200, MirrorSample.bytes, 40)
        }
    }

    @Test
    fun stopKeepsFinishedRowsAndMarksOnlyTheUnfinishedOnes() = runTest {
        val runner = MirrorProbeRunner(MirrorProbe(transport(slow = setOf("b.example", "c.example"))), backgroundScope)
        runner.measureAll(listOf("direct" to null, "b" to entry("b"), "c" to entry("c")))
        runCurrent()
        // Four permits: the direct probe finishes at once, the two slow ones stay in flight.
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
        val transport = object : MirrorTransport {
            override suspend fun fetch(url: String, timeoutMillis: Long, maxBytes: Int): ProbeResponse {
                val run = generation
                delay(if (run == 0) 5_000 else 10)
                return ProbeResponse(200, MirrorSample.bytes, if (run == 0) 999 else 30)
            }
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
    fun aStoppedRunIsNotReportedAsAnError() = runTest {
        val runner = MirrorProbeRunner(MirrorProbe(transport(slow = setOf("b.example"))), backgroundScope)
        runner.measureAll(listOf("b" to entry("b")))
        runCurrent()
        runner.stop()
        assertNull(runner.result("b"))
        assertEquals(MirrorTone.Muted, mirrorTone(runner.result("b"), runner.phase("b")))
        runCurrent()
        assertTrue(runner.stopped)
    }
}
