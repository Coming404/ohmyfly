package com.ohmyfly.subscription

import com.ohmyfly.nativecore.NativeCoreException
import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SubscriptionFailuresTest {
    @Test
    fun allHttpFailuresReturnTheFirstFailureAndStillTryEveryProfile() = runTest {
        var requests = 0
        val client = subscriptionClient {
            requests++
            respond("", if (requests == 1) HttpStatusCode.NotFound else HttpStatusCode.BadRequest)
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { error("must not parse") })
            val result = importer.importSubscription("https://sub.example/p", SubscriptionSettings())
            assertEquals("HTTP 404", result.failure)
            assertEquals(emptyList(), result.nodes)
            assertEquals(4, requests)
        } finally { client.close() }
    }

    @Test
    fun successfulParseWinsOverHttpFailuresBeforeAndAfterIt() = runTest {
        var requests = 0
        var parsed = 0
        val client = subscriptionClient {
            requests++
            if (requests == 2) respondOk("good") else respond("", HttpStatusCode.NotFound)
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { parsed++; testSnapshot(testNode("good")) })
            val result = importer.importSubscription("https://sub.example/p", SubscriptionSettings())
            assertNull(result.failure)
            assertEquals(listOf("good"), result.nodes.map { it.name })
            assertEquals(1, parsed)
            assertEquals(4, requests)
        } finally { client.close() }
    }

    @Test
    fun parsingExceptionsDoNotRetryOrExposePrivateDetails() = runTest {
        var requests = 0
        val client = subscriptionClient { requests++; respondOk("private profile") }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { throw NativeCoreException("password=private") })
            val result = importer.importSubscription("https://sub.example/p", SubscriptionSettings(userAgent = "a|b", retries = 5))
            assertEquals("订阅解析失败", result.failure)
            assertEquals(2, requests)
        } finally { client.close() }
    }

    @Test
    fun emptyParsedSnapshotTakesPrecedenceOverTransportFailure() = runTest {
        var requests = 0
        val client = subscriptionClient {
            requests++
            if (requests == 2) respondOk("empty") else respond("", HttpStatusCode.NotFound)
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot() })
            assertEquals("未解析到有效节点", importer.importSubscription("https://sub.example/p", SubscriptionSettings()).failure)
        } finally { client.close() }
    }

    @Test
    fun declaredOversizeIsRejectedWithoutParsingOrRetrying() = runTest {
        var requests = 0
        var parsed = 0
        val client = subscriptionClient {
            requests++
            respond(ByteReadChannel("small"), headers = headersOf(HttpHeaders.ContentLength, "1048577"))
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { parsed++; testSnapshot(testNode()) })
            val result = importer.importSubscription("https://sub.example/p", SubscriptionSettings(maxMiB = 1, userAgent = "a|b", retries = 5))
            assertEquals("订阅超过大小上限", result.failure)
            assertEquals(2, requests)
            assertEquals(0, parsed)
        } finally { client.close() }
    }

    @Test
    fun streamedOversizeIsRejectedWithoutContentLength() = runTest {
        var requests = 0
        val client = subscriptionClient { requests++; respond(ByteReadChannel(ByteArray(1048577))) }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { error("must not parse") })
            val result = importer.importSubscription("https://sub.example/p", SubscriptionSettings(maxMiB = 1, userAgent = "a", retries = 5))
            assertEquals("订阅超过大小上限", result.failure)
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test
    fun bodyExactlyAtSizeLimitIsAccepted() = runTest {
        val client = subscriptionClient { respond(ByteReadChannel(ByteArray(1048576) { 120 })) }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), {
                assertEquals(1048576, it.length)
                testSnapshot(testNode())
            })
            assertNull(importer.importSubscription("https://sub.example/p", SubscriptionSettings(maxMiB = 1, userAgent = "a")).failure)
        } finally { client.close() }
    }

    @Test
    fun retryableStatusesRetryTheSameAgentBeforeMovingOn() = runTest {
        for (status in listOf(503, 429, 401, 403)) {
            val agents = mutableListOf<String?>()
            val client = subscriptionClient { request ->
                agents += request.headers[HttpHeaders.UserAgent]
                if (agents.size == 1) respond("", HttpStatusCode.fromValue(status)) else respondOk("p")
            }
            try {
                val importer = SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) })
                val result = importer.importSubscription("https://sub.example/p", SubscriptionSettings(userAgent = "a|b", retries = 1))
                assertNull(result.failure)
                assertEquals(listOf("a", "a", "b"), agents)
            } finally { client.close() }
        }
    }

    @Test
    fun exhaustedRetriesStillAllowLaterAgents() = runTest {
        val agents = mutableListOf<String?>()
        val client = subscriptionClient { request ->
            agents += request.headers[HttpHeaders.UserAgent]
            respond("", HttpStatusCode.ServiceUnavailable)
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { error("must not parse") })
            val result = importer.importSubscription("https://sub.example/p", SubscriptionSettings(userAgent = "a|b", retries = 2))
            assertEquals("HTTP 503", result.failure)
            assertEquals(listOf("a", "a", "a", "b", "b", "b"), agents)
        } finally { client.close() }
    }

    @Test
    fun githubDefaultModeDoesNotRetryEvenRetryableStatus() = runTest {
        var requests = 0
        val client = subscriptionClient { request ->
            requests++
            assertNull(request.headers[HttpHeaders.UserAgent])
            respond("", HttpStatusCode.ServiceUnavailable)
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { error("must not parse") })
            assertEquals("HTTP 503", importer.importSubscription(
                "https://raw.githubusercontent.com/p", SubscriptionSettings(retries = 5),
            ).failure)
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test
    fun networkExceptionsAdvanceWithoutUnapprovedRetries() = runTest {
        var requests = 0
        val client = subscriptionClient { requests++; throw IllegalStateException("private address") }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { error("must not parse") })
            val result = importer.importSubscription("https://sub.example/p", SubscriptionSettings(userAgent = "a|b", retries = 5))
            assertEquals("订阅请求失败", result.failure)
            assertEquals(2, requests)
        } finally { client.close() }
    }

    @Test
    fun transportCancellationIsRethrownWithoutFurtherAttempts() = runTest {
        var requests = 0
        val client = subscriptionClient { requests++; throw CancellationException("stop") }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { error("must not parse") })
            assertFailsWith<CancellationException> { importer.importSubscription("https://sub.example/p", SubscriptionSettings()) }
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test
    fun parserCancellationIsRethrownWithoutFurtherAttempts() = runTest {
        var requests = 0
        val client = subscriptionClient { requests++; respondOk("p") }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { throw CancellationException("stop") })
            assertFailsWith<CancellationException> { importer.importSubscription("https://sub.example/p", SubscriptionSettings()) }
            assertEquals(1, requests)
        } finally { client.close() }
    }
}
