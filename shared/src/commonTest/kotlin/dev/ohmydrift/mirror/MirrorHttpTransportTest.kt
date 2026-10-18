package dev.ohmydrift.mirror

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MirrorHttpTransportTest {
    @Test
    fun streamCapIsEnforcedWithoutContentLength() = runTest {
        val client = HttpClient(MockEngine { respond(ByteReadChannel(ByteArray(33))) }) { mirrorHttpPolicy() }
        try {
            val transport = MirrorHttpTransport(client, verifyHost = {})
            assertFailsWith<MirrorFetchException> { transport.fetch("https://example.com/file", 5000, 8) }
        } finally { client.close() }
    }

    @Test
    fun declaredOversizeIsRejectedBeforeReading() = runTest {
        val client = HttpClient(MockEngine {
            respond(ByteReadChannel(ByteArray(32)), headers = headersOf(HttpHeaders.ContentLength, "32"))
        }) { mirrorHttpPolicy() }
        try {
            assertFailsWith<MirrorFetchException> { MirrorHttpTransport(client, {}).fetch("https://example.com/file", 5000, 8) }
        } finally { client.close() }
    }

    @Test
    fun redirectsAreReturnedNotAutomaticallyFollowed() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond(ByteReadChannel(byteArrayOf()), HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://127.0.0.1/"))
        }) { mirrorHttpPolicy() }
        try {
            val response = MirrorHttpTransport(client, {}).fetch("https://example.com/file", 5000, 8)
            assertEquals(302, response.statusCode)
            assertEquals(1, calls)
            assertEquals("https://127.0.0.1/", response.location)
        } finally { client.close() }
    }

    @Test
    fun cookiesAndAuthorizationAreNeverAdded() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[HttpHeaders.Cookie])
            respond(ByteReadChannel("abc".encodeToByteArray()), headers = headersOf(HttpHeaders.SetCookie, "session=secret"))
        }) { mirrorHttpPolicy() }
        try {
            val transport = MirrorHttpTransport(client, {})
            repeat(2) { assertEquals(3, transport.fetch("https://example.com/file", 5000, 8).body.size) }
        } finally { client.close() }
    }
}
