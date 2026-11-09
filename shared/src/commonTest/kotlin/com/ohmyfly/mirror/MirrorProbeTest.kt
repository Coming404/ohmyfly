package com.ohmyfly.mirror

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.*
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class MirrorProbeTest {
    @Test
    fun fixedFixtureMatchesIndependentlyCheckedDigest() {
        assertEquals(425, MirrorSample.bytes.size)
        assertEquals(MirrorSample.sha256, digest(MirrorSample.bytes))
    }

    @Test
    fun oneHeadUsesReadmeWithAnEpochCacheBusterForMirrorAndDirect() = runTest {
        val expectedResource = "https://raw.githubusercontent.com/microsoft/vscode/main/README.md"
        for (entry in listOf(mirrors().first(), null)) {
            var calls = 0
            val before = Clock.System.now().toEpochMilliseconds()
            val probe = MirrorProbe(headFake { url, timeout ->
                calls++
                val expected = entry?.let { "https://a.example.com/$expectedResource" } ?: expectedResource
                assertEquals(expected, url.substringBefore("?_t="))
                val timestamp = url.substringAfter("?_t=").toLong()
                assertTrue(timestamp in before..Clock.System.now().toEpochMilliseconds())
                assertEquals(4_000L, timeout)
                MirrorHeadResponse(200, 17)
            })
            val result = probe.measure(entry)
            assertEquals(1, calls)
            assertEquals(ProbeStatus.Success, result.status)
            assertEquals(17L, result.latencyMs)
            assertEquals("HTTP 200", result.detail)
        }
    }

    @Test
    fun successIncludesAll2xxAnd3xxButNeverOtherStatuses() = runTest {
        for (status in listOf(199, 200, 204, 299, 300, 302, 399, 400, 404, 405, 500, 503)) {
            var calls = 0
            val result = MirrorProbe(headFake { _, _ ->
                calls++
                MirrorHeadResponse(status, 23)
            }).measure(null)
            val success = status in 200..399
            assertEquals(if (success) ProbeStatus.Success else ProbeStatus.Failed, result.status)
            assertEquals(if (success) 23L else null, result.latencyMs)
            assertEquals("HTTP $status", result.detail)
            assertEquals(1, calls)
        }
    }

    @Test
    fun wholeProbeTimesOutAfterFourSecondsWithoutRetry() = runTest {
        var calls = 0
        val result = MirrorProbe(headFake { _, _ ->
            calls++
            delay(4_001)
            MirrorHeadResponse(200, 4_001)
        }).measure(null)
        assertEquals(ProbeStatus.Timeout, result.status)
        assertNull(result.latencyMs)
        assertEquals(4_000L, testScheduler.currentTime)
        assertEquals(1, calls)
    }

    @Test
    fun transportTimeoutAndNetworkFailuresDoNotRetry() = runTest {
        val failures = listOf(MirrorTimeoutException(), MirrorFetchException("blocked host"), IllegalStateException("network"))
        for (failure in failures) {
            var calls = 0
            val result = MirrorProbe(headFake { _, _ -> calls++; throw failure }).measure(null)
            assertEquals(if (failure is MirrorTimeoutException) ProbeStatus.Timeout else ProbeStatus.Failed, result.status)
            assertNull(result.latencyMs)
            assertEquals(1, calls)
        }
    }

    @Test
    fun invalidMirrorNeverReachesTheNetwork() = runTest {
        val probe = MirrorProbe(headFake { _, _ -> error("Invalid mirror must not send HEAD") })
        assertEquals(ProbeStatus.Failed, probe.measure(MirrorEntry("a", "A", "https://127.0.0.1/")).status)
    }

    @Test
    fun callerTimeoutPropagatesWithoutRetry() = runTest {
        var calls = 0
        val probe = MirrorProbe(headFake { _, _ -> calls++; delay(1000); MirrorHeadResponse(200, 1000) })
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
        val result = fetcher.fetch(MirrorSample.url, "b", entries, expectedSha256 = MirrorSample.sha256)
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
        }).fetch(MirrorSample.url, null, mirrors(), expectedSha256 = MirrorSample.sha256)
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
        }).fetch(MirrorSample.url, "a", mirrors(), timeoutMillis = 10, expectedSha256 = MirrorSample.sha256)
        assertEquals("c", result.usedMirrorId)
        assertEquals(listOf("a", "b"), result.failedMirrorIds)
    }

    @Test
    fun cancellationNeverTriesAnotherMirror() = runTest {
        var calls = 0
        val fetcher = MirrorResourceFetcher(fake { _, _, _ -> calls++; throw CancellationException("cancel") })
        assertFailsWith<CancellationException> { fetcher.fetch(MirrorSample.url, "a", mirrors()) }
        assertEquals(1, calls)
    }

    @Test
    fun disabledMirrorsOnlyUseDirectEvenWithAStaleSelection() = runTest {
        val calls = mutableListOf<String>()
        val result = MirrorResourceFetcher(fake { url, _, _ ->
            calls += parseHttpsUrl(url)!!.host
            ProbeResponse(200, MirrorSample.bytes, 12)
        }).fetch(
            originalUrl = MirrorSample.url,
            // 停用后仍保留的旧选择：既不能参与排序，也不能报“选中的镜像不存在”。
            selectedMirrorId = "gone",
            mirrors = mirrors(),
            expectedSha256 = MirrorSample.sha256,
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
        assertFailsWith<MirrorFetchException> { fetcher.fetch(MirrorSample.url, "a", mirrors(), enabled = false) }
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
            cancelling.fetch(MirrorSample.url, "a", mirrors(), enabled = false)
        }
    }

    @Test
    fun enabledWithEmptyMirrorListUsesDirectFallback() = runTest {
        val calls = mutableListOf<String>()
        val result = MirrorResourceFetcher(fake { url, _, _ ->
            calls += parseHttpsUrl(url)!!.host
            ProbeResponse(200, MirrorSample.bytes, 1)
        }).fetch(
            originalUrl = MirrorSample.url,
            selectedMirrorId = null,
            mirrors = emptyList(),
            expectedSha256 = MirrorSample.sha256,
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
        }).fetch(MirrorSample.url, "deleted", entries, expectedSha256 = MirrorSample.sha256)
        assertEquals("a", result.usedMirrorId)
        assertEquals(1, calls.size)
        assertTrue(calls.single().startsWith("http://a.example.com/https://"))
    }

    private fun mirrors() = listOf("a", "b", "c").map { MirrorEntry(it, it.uppercase(), "https://$it.example.com/") }
    private fun headFake(block: suspend (String, Long) -> MirrorHeadResponse): MirrorTransport = object : MirrorTransport {
        override suspend fun head(url: String, timeoutMillis: Long): MirrorHeadResponse = block(url, timeoutMillis)
        override suspend fun fetch(url: String, timeoutMillis: Long, maxBytes: Int): ProbeResponse =
            error("HEAD probe must never fall back to GET")
    }

    private fun fake(block: suspend (String, Long, Int) -> ProbeResponse): MirrorTransport = object : MirrorTransport {
        override suspend fun head(url: String, timeoutMillis: Long): MirrorHeadResponse =
            error("Resource download must use GET")
        override suspend fun fetch(url: String, timeoutMillis: Long, maxBytes: Int): ProbeResponse = block(url, timeoutMillis, maxBytes)
    }
}
