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
    fun absentSelectionStartsWithMirrorsAndDirectIsOnlyTheFinalFallback() = runTest {
        val calls = mutableListOf<String>()
        val result = MirrorResourceFetcher(fake { url, _, _ ->
            val host = parseHttpsUrl(url)!!.host
            calls += host
            if (host == "raw.githubusercontent.com") ProbeResponse(200, MirrorSample.bytes, 10)
            else ProbeResponse(500, byteArrayOf(), 1)
        }).fetch(MirrorProbeTarget.url, null, mirrors(), expectedSha256 = MirrorProbeTarget.sha256)
        assertEquals(listOf("a.example.com", "b.example.com", "c.example.com", "raw.githubusercontent.com"), calls)
        assertNull(result.usedMirrorId)
        assertEquals(listOf("a", "b", "c"), result.failedMirrorIds)
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

    @Test
    fun disabledMirrorsOnlyUseDirectEvenWithAStaleSelection() = runTest {
        val calls = mutableListOf<String>()
        val result = MirrorResourceFetcher(fake { url, _, _ ->
            calls += parseHttpsUrl(url)!!.host
            ProbeResponse(200, MirrorSample.bytes, 12)
        }).fetch(
            originalUrl = MirrorProbeTarget.url,
            // 停用后仍保留的旧选择：既不能参与排序，也不能报“选中的镜像不存在”。
            selectedMirrorId = "gone",
            mirrors = mirrors(),
            expectedSha256 = MirrorProbeTarget.sha256,
            enabled = false,
        )
        assertEquals(listOf("raw.githubusercontent.com"), calls)
        assertNull(result.usedMirrorId)
        assertTrue(result.failedMirrorIds.isEmpty())
    }

    @Test
    fun disabledDirectFailureDoesNotFallBackToMirrors() = runTest {
        var calls = 0
        val fetcher = MirrorResourceFetcher(fake { _, _, _ -> calls++; ProbeResponse(503, byteArrayOf(), 1) })
        assertFailsWith<MirrorFetchException> { fetcher.fetch(MirrorProbeTarget.url, "a", mirrors(), enabled = false) }
        assertEquals(1, calls)
    }

    @Test
    fun disabledStillKeepsUrlSafetyAndCancellation() = runTest {
        var calls = 0
        val fetcher = MirrorResourceFetcher(fake { _, _, _ -> calls++; ProbeResponse(200, MirrorSample.bytes, 1) })
        assertFailsWith<MirrorFetchException> {
            fetcher.fetch("http://raw.githubusercontent.com/a/b/c/d", null, mirrors(), enabled = false)
        }
        assertEquals(0, calls)

        val cancelling = MirrorResourceFetcher(fake { _, _, _ -> throw CancellationException("cancel") })
        assertFailsWith<CancellationException> {
            cancelling.fetch(MirrorProbeTarget.url, "a", mirrors(), enabled = false)
        }
    }

    @Test
    fun enabledWithEmptyMirrorListUsesDirectFallback() = runTest {
        val calls = mutableListOf<String>()
        val result = MirrorResourceFetcher(fake { url, _, _ ->
            calls += parseHttpsUrl(url)!!.host
            ProbeResponse(200, MirrorSample.bytes, 1)
        }).fetch(
            originalUrl = MirrorProbeTarget.url,
            selectedMirrorId = null,
            mirrors = emptyList(),
            expectedSha256 = MirrorProbeTarget.sha256,
            enabled = true,
        )

        assertEquals(listOf("raw.githubusercontent.com"), calls)
        assertNull(result.usedMirrorId)
    }


    @Test
    fun staleSelectionStartsWithFirstMirrorAndHttpRewriteIsUsed() = runTest {
        val calls = mutableListOf<String>()
        val entries = mirrors().map { it.copy(address = it.address.replace("https://", "http://")) }
        val result = MirrorResourceFetcher(fake { url, _, _ ->
            calls += url
            ProbeResponse(200, MirrorSample.bytes, 5)
        }).fetch(MirrorProbeTarget.url, "deleted", entries, expectedSha256 = MirrorProbeTarget.sha256)
        assertEquals("a", result.usedMirrorId)
        assertEquals(1, calls.size)
        assertTrue(calls.single().startsWith("http://a.example.com/https://"))
    }

    private fun mirrors() = listOf("a", "b", "c").map { MirrorEntry(it, it.uppercase(), "https://$it.example.com/") }
    private fun fake(block: suspend (String, Long, Int) -> ProbeResponse): MirrorTransport = object : MirrorTransport {
        override suspend fun fetch(url: String, timeoutMillis: Long, maxBytes: Int): ProbeResponse = block(url, timeoutMillis, maxBytes)
    }
}
