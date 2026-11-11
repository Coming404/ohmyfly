// 验证用量随同次 GET 的选中根快照传播，不混入其他候选或子订阅。
package com.ohmyfly.subscription

import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionUsageImportTest {
    @Test
    fun fetchReturnsBodyAndUsageFromTheSameGetWithoutChangingRequestHeaders() = runTest {
        var requests = 0
        val client = subscriptionClient { request ->
            requests++
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("custom-agent", request.headers[HttpHeaders.UserAgent])
            assertEquals("test-token", request.headers[HttpHeaders.Authorization])
            assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
            respond("original body", headers = headersOf("Subscription-UserInfo", "upload=0;download=8;expire=1800000000"))
        }
        try {
            val result = SubscriptionFetcher(client).fetch(
                "https://sub.example/profile", SubscriptionSettings(), "original-agent", 10_000,
                mapOf(HttpHeaders.UserAgent to listOf("custom-agent"), HttpHeaders.Authorization to listOf("test-token")),
            )
            assertEquals("original body", result.body)
            assertEquals(SubscriptionUsage(upload = 0, download = 8, expireEpochSeconds = 1_800_000_000), result.usage)
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test
    fun usageFollowsTheWinningUserAgentSnapshotWithoutMergingFields() = runTest {
        val calls = mutableListOf<String?>()
        val client = subscriptionClient { request ->
            val agent = request.headers[HttpHeaders.UserAgent]
            calls += agent
            respond(agent.orEmpty(), headers = headersOf(SubscriptionUsageHeader,
                if (agent == "one") "upload=3;total=100" else "download=9"))
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), {
                if (it == "one") testSnapshot(testNode()) else testSnapshot(testNode(), testNode("JP", server = "jp.example"))
            })
            val result = importer.importSubscription("https://sub.example/profile", SubscriptionSettings(userAgent = "one|two"))
            assertNull(result.failure)
            assertEquals(2, result.nodes.size)
            assertEquals(SubscriptionUsage(download = 9), result.usage)
            assertEquals(listOf("one", "two"), calls)
        } finally { client.close() }
    }

    @Test
    fun winningSnapshotWithoutUsageDoesNotBorrowItFromAnotherCandidate() = runTest {
        var requests = 0
        val client = subscriptionClient { request ->
            requests++
            val agent = request.headers[HttpHeaders.UserAgent]
            respond(agent.orEmpty(), headers = if (agent == "one") Headers.Empty
                else headersOf(SubscriptionUsageHeader, "total=999"))
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), {
                if (it == "one") testSnapshot(testNode(), testNode("JP", server = "jp.example")) else testSnapshot(testNode())
            })
            val result = importer.importSubscription("https://sub.example/profile", SubscriptionSettings(userAgent = "one|two"))
            assertEquals(2, result.nodes.size)
            assertNull(result.usage)
            assertEquals(2, requests)
        } finally { client.close() }
    }

    @Test
    fun equivalentCandidatesKeepTheFirstSnapshotsUsage() = runTest {
        val client = subscriptionClient { request ->
            respond("body", headers = headersOf(SubscriptionUsageHeader,
                if (request.headers[HttpHeaders.UserAgent] == "one") "total=10" else "total=20"))
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) })
            val result = importer.importSubscription("https://sub.example/profile", SubscriptionSettings(userAgent = "one|two"))
            assertEquals(SubscriptionUsage(total = 10), result.usage)
        } finally { client.close() }
    }

    @Test
    fun providerUsageNeverReplacesRootUsageEvenWhenRootHasNoHeader() = runTest {
        for (rootUsage in listOf(null, "total=10")) {
            val calls = mutableListOf<String>()
            val client = subscriptionClient { request ->
                val path = request.url.encodedPath
                calls += path
                val usage = if (path == "/root") rootUsage else "total=999"
                respond(path, headers = if (usage == null) Headers.Empty else headersOf(SubscriptionUsageHeader, usage))
            }
            try {
                val importer = SubscriptionImporter(SubscriptionFetcher(client), {
                    if (it == "/root") providerSnapshot(testProvider("child")) else testSnapshot(testNode())
                })
                val result = importer.importSubscription("https://sub.example/root", SubscriptionSettings(userAgent = "one"))
                assertNull(result.failure)
                assertEquals(1, result.nodes.size)
                assertEquals(if (rootUsage == null) null else SubscriptionUsage(total = 10), result.usage)
                assertEquals(listOf("/root", "/child"), calls)
            } finally { client.close() }
        }
    }

    @Test
    fun failedHttpResponseCannotPublishUsageOrReachTheNodeParser() = runTest {
        val client = subscriptionClient {
            respond("error page", HttpStatusCode.NotFound, headersOf(SubscriptionUsageHeader, "total=10"))
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { error("must not parse HTTP errors") })
            val result = importer.importSubscription("https://sub.example/profile", SubscriptionSettings(userAgent = "one"))
            assertEquals("HTTP 404", result.failure)
            assertNull(result.usage)
        } finally { client.close() }
    }
}
