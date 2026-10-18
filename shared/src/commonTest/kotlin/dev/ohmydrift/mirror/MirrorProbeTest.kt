package dev.ohmydrift.mirror

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class MirrorProbeTest {
    @Test
    fun fixedFixtureMatchesIndependentlyCheckedDigest() {
        assertEquals(425, MirrorSample.bytes.size)
        assertEquals(MirrorProbeTarget.sha256, digest(MirrorSample.bytes))
    }

    @Test
    fun onlyVerifiedSamplesContributeToMedian() = runTest {
        var count = 0
        val transport = fake { _, _, _ ->
            count++
            when (count) {
                1 -> ProbeResponse(200, MirrorSample.bytes, 10)
                2 -> ProbeResponse(200, "not the file".encodeToByteArray(), 1)
                else -> ProbeResponse(200, MirrorSample.bytes, 30)
            }
        }
        val result = MirrorProbe(transport).measure(null)
        assertEquals(ProbeStatus.Success, result.status)
        assertEquals(20L, result.latencyMs)
        assertEquals(2, result.successes)
    }

    @Test
    fun oversizedOrWrongContentNeverBecomesLowLatencySuccess() = runTest {
        val wrong = MirrorProbe(fake { _, _, cap -> ProbeResponse(200, ByteArray(cap + 1), 0) }).measure(null)
        assertEquals(ProbeStatus.InvalidContent, wrong.status)
        assertNull(wrong.latencyMs)
        val denied = MirrorProbe(fake { _, _, _ -> ProbeResponse(403, MirrorSample.bytes, 1) }).measure(null)
        assertEquals(ProbeStatus.Failed, denied.status)
    }

    @Test
    fun callerTimeoutIsNotConvertedIntoThreeFailedSamples() = runTest {
        var calls = 0
        val probe = MirrorProbe(fake { _, _, _ -> calls++; delay(1000); ProbeResponse(200, MirrorSample.bytes, 1000) })
        assertFailsWith<TimeoutCancellationException> { withTimeout(10) { probe.measure(null) } }
        assertEquals(1, calls)
    }

    @Test
    fun selectedMirrorFallsBackInPersistedOrderNotLatencyOrder() = runTest {
        val entries = mirrors()
        val calls = mutableListOf<String>()
        val fetcher = MirrorResourceFetcher(fake { url, _, _ ->
            val host = parseHttpsUrl(url)!!.host
            calls += host
            if (host == "c.example.com") ProbeResponse(200, MirrorSample.bytes, 50)
            else ProbeResponse(503, byteArrayOf(), 1)
        })
        val result = fetcher.fetch(MirrorProbeTarget.url, "b", entries, expectedSha256 = MirrorProbeTarget.sha256)
        assertEquals(listOf("b.example.com", "a.example.com", "c.example.com"), calls)
        assertEquals("c", result.usedMirrorId)
        assertEquals(listOf("b", "a"), result.failedMirrorIds)
    }

    @Test
    fun directSelectionReallyStartsWithDirect() = runTest {
        val calls = mutableListOf<String>()
        val result = MirrorResourceFetcher(fake { url, _, _ ->
            calls += parseHttpsUrl(url)!!.host
            if (calls.size == 1) ProbeResponse(500, byteArrayOf(), 1) else ProbeResponse(200, MirrorSample.bytes, 10)
        }).fetch(MirrorProbeTarget.url, null, mirrors(), expectedSha256 = MirrorProbeTarget.sha256)
        assertEquals(listOf("raw.githubusercontent.com", "a.example.com"), calls)
        assertEquals("a", result.usedMirrorId)
        assertEquals(listOf("direct"), result.failedMirrorIds)
    }

    @Test
    fun ownTimeoutAndBadContentBothAllowFallback() = runTest {
        val result = MirrorResourceFetcher(fake { url, _, _ ->
            when (parseHttpsUrl(url)!!.host) {
                "a.example.com" -> { delay(1000); ProbeResponse(200, MirrorSample.bytes, 1) }
                "b.example.com" -> ProbeResponse(200, "error page".encodeToByteArray(), 1)
                else -> ProbeResponse(200, MirrorSample.bytes, 1)
            }
        }).fetch(MirrorProbeTarget.url, "a", mirrors(), timeoutMillis = 10, expectedSha256 = MirrorProbeTarget.sha256)
        assertEquals("c", result.usedMirrorId)
        assertEquals(listOf("a", "b"), result.failedMirrorIds)
    }

    @Test
    fun cancellationNeverTriesAnotherMirror() = runTest {
        var calls = 0
        val fetcher = MirrorResourceFetcher(fake { _, _, _ -> calls++; throw CancellationException("cancel") })
        assertFailsWith<CancellationException> { fetcher.fetch(MirrorProbeTarget.url, "a", mirrors()) }
        assertEquals(1, calls)
    }

    private fun mirrors() = listOf("a", "b", "c").map { MirrorEntry(it, it.uppercase(), "https://$it.example.com/") }
    private fun fake(block: suspend (String, Long, Int) -> ProbeResponse): MirrorTransport = object : MirrorTransport {
        override suspend fun fetch(url: String, timeoutMillis: Long, maxBytes: Int): ProbeResponse = block(url, timeoutMillis, maxBytes)
    }
}
