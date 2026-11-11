// 验证名称复用既有 GET，并且只属于选中的根订阅响应。
package com.ohmyfly.subscription

import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionNameImportTest {
    @Test
    fun sameGetSuppliesNameUsageAndBodyWithoutExtraRequests() = runTest {
        var requests = 0
        val client = subscriptionClient { request ->
            requests++
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("one", request.headers[HttpHeaders.UserAgent])
            respond("nodes-body", headers = headersOf(
                "Profile-Title" to listOf("  Remote.yaml  "),
                SubscriptionUsageHeader to listOf("total=42"),
            ))
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), {
                assertEquals("nodes-body", it)
                testSnapshot(testNode())
            })
            val result = importer.importSubscription("https://sub.example/profile", SubscriptionSettings(userAgent = "one"))
            assertNull(result.failure)
            assertEquals("Remote.yaml", result.name)
            assertEquals(SubscriptionUsage(total = 42), result.usage)
            assertEquals(1, result.nodes.size)
            assertEquals(1, requests)
            val named = importer.importSubscription(
                "https://sub.example/profile", SubscriptionSettings(userAgent = "one"), resolveName = false,
            )
            assertNull(named.failure)
            assertNull(named.name)
            assertEquals(result.nodes, named.nodes)
            assertEquals(result.usage, named.usage)
            assertEquals(2, requests)
        } finally { client.close() }
    }

    @Test
    fun selectedSnapshotKeepsItsOwnNameIncludingMissingMetadata() = runTest {
        for (selectedName in listOf(null, "Winner")) {
            var requests = 0
            val client = subscriptionClient { request ->
                requests++
                val agent = request.headers[HttpHeaders.UserAgent].orEmpty()
                val name = if (agent == "two") selectedName else "Losing candidate"
                respond(agent, headers = name?.let { headersOf("profile-title", it) } ?: Headers.Empty)
            }
            try {
                val importer = SubscriptionImporter(SubscriptionFetcher(client), {
                    if (it == "one") testSnapshot(testNode())
                    else testSnapshot(testNode(), testNode("JP", server = "jp.example"))
                })
                val result = importer.importSubscription("https://sub.example/profile", SubscriptionSettings(userAgent = "one|two"))
                assertNull(result.failure)
                assertEquals(2, result.nodes.size)
                assertEquals(selectedName, result.name)
                assertEquals(2, requests)
            } finally { client.close() }
        }
    }

    @Test
    fun providerNameNeverFillsOrOverridesRootName() = runTest {
        for (rootName in listOf(null, "Root")) {
            val paths = mutableListOf<String>()
            val client = subscriptionClient { request ->
                val path = request.url.encodedPath
                paths += path
                val name = if (path == "/root") rootName else "Child"
                respond(path, headers = name?.let { headersOf("profile-title", it) } ?: Headers.Empty)
            }
            try {
                val importer = SubscriptionImporter(SubscriptionFetcher(client), {
                    if (it == "/root") providerSnapshot(testProvider("child")) else testSnapshot(testNode())
                })
                val result = importer.importSubscription("https://sub.example/root", SubscriptionSettings(userAgent = "one"))
                assertNull(result.failure)
                assertEquals(rootName, result.name)
                assertEquals(1, result.nodes.size)
                assertEquals(listOf("/root", "/child"), paths)
            } finally { client.close() }
        }
    }

    @Test
    fun invalidNameMetadataDoesNotFailNodesOrUsage() = runTest {
        val client = subscriptionClient {
            respond("nodes", headers = headersOf(
                "profile-title" to listOf("base64:/w=="),
                HttpHeaders.ContentDisposition to listOf("inline; filename*=UTF-8''%FF"),
                SubscriptionUsageHeader to listOf("download=8"),
            ))
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) })
            val result = importer.importSubscription("https://sub.example/profile", SubscriptionSettings(userAgent = "one"))
            assertNull(result.failure)
            assertNull(result.name)
            assertEquals(1, result.nodes.size)
            assertEquals(SubscriptionUsage(download = 8), result.usage)
        } finally { client.close() }
    }
}
