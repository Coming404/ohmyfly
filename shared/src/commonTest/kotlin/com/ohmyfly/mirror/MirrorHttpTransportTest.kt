package com.ohmyfly.mirror

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MirrorHttpTransportTest {
    // The engine and withTimeout must share runTest's scheduler; real IO dispatchers race virtual deadlines.
    private fun TestScope.mockClient(handler: MockRequestHandler) = HttpClient(MockEngine) {
        mirrorHttpPolicy()
        engine {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        }
    }

    @Test
    fun headReturnsAfterHeadersWithoutWaitingForOrReadingBody() = runTest {
        val body = ByteChannel()
        var calls = 0
        val hosts = mutableListOf<String>()
        val client = mockClient { request ->
            calls++
            assertEquals(HttpMethod.Head, request.method)
            assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[HttpHeaders.Cookie])
            // The body stays open and empty: trying to read it would hit the four-second deadline.
            respond(body, headers = headersOf(HttpHeaders.ContentLength, "99999999"))
        }
        try {
            val response = MirrorHttpTransport(client, { hosts += it }).head("https://example.com/file", 4_000)
            assertEquals(200, response.statusCode)
            assertTrue(response.latencyMs >= 0)
            assertEquals(0L, testScheduler.currentTime)
            assertEquals(listOf("example.com"), hosts)
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test
    fun headAcceptsRedirectResponseWithoutRequestingItsLocation() = runTest {
        var calls = 0
        val client = mockClient { request ->
            calls++
            assertEquals(HttpMethod.Head, request.method)
            respond(ByteReadChannel(byteArrayOf()), HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://127.0.0.1/"))
        }
        try {
            val result = MirrorProbe(MirrorHttpTransport(client, {})).measure(null)
            assertEquals(ProbeStatus.Success, result.status)
            assertEquals("HTTP 302", result.detail)
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test
    fun headDeadlineIncludesHostVerificationAndResponseWait() = runTest {
        var calls = 0
        val client = mockClient {
            calls++
            delay(2_000)
            respond(ByteReadChannel(byteArrayOf()))
        }
        try {
            val transport = MirrorHttpTransport(client, { delay(2_500) })
            assertFailsWith<MirrorTimeoutException> { transport.head("https://example.com/file", 4_000) }
            assertEquals(4_000L, testScheduler.currentTime)
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test
    fun headNeverRequestsAHostRejectedByPublicDnsVerification() = runTest {
        var calls = 0
        val client = mockClient { calls++; respond(ByteReadChannel(byteArrayOf())) }
        try {
            val transport = MirrorHttpTransport(client, { throw MirrorFetchException("private DNS address") })
            assertFailsWith<MirrorFetchException> { transport.head("https://example.com/file", 4_000) }
            assertEquals(0, calls)
        } finally { client.close() }
    }

    @Test
    fun streamCapIsEnforcedWithoutContentLength() = runTest {
        val client = mockClient { respond(ByteReadChannel(ByteArray(33))) }
        try {
            val transport = MirrorHttpTransport(client, verifyHost = {})
            assertFailsWith<MirrorFetchException> { transport.fetch("https://example.com/file", 5000, 8) }
        } finally { client.close() }
    }

    @Test
    fun declaredOversizeIsRejectedBeforeReading() = runTest {
        val client = mockClient {
            respond(ByteReadChannel(ByteArray(32)), headers = headersOf(HttpHeaders.ContentLength, "32"))
        }
        try {
            assertFailsWith<MirrorFetchException> { MirrorHttpTransport(client, {}).fetch("https://example.com/file", 5000, 8) }
        } finally { client.close() }
    }

    @Test
    fun redirectsAreReturnedNotAutomaticallyFollowed() = runTest {
        var calls = 0
        val client = mockClient {
            calls++
            respond(ByteReadChannel(byteArrayOf()), HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://127.0.0.1/"))
        }
        try {
            val response = MirrorHttpTransport(client, {}).fetch("https://example.com/file", 5000, 8)
            assertEquals(302, response.statusCode)
            assertEquals(1, calls)
            assertEquals("https://127.0.0.1/", response.location)
        } finally { client.close() }
    }

    @Test
    fun cookiesAndAuthorizationAreNeverAdded() = runTest {
        val client = mockClient { request ->
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[HttpHeaders.Cookie])
            respond(ByteReadChannel("abc".encodeToByteArray()), headers = headersOf(HttpHeaders.SetCookie, "session=secret"))
        }
        try {
            val transport = MirrorHttpTransport(client, {})
            repeat(2) { assertEquals(3, transport.fetch("https://example.com/file", 5000, 8).body.size) }
        } finally { client.close() }
    }
}
