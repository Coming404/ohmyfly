package dev.ohmydrift.mirror

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.*

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
