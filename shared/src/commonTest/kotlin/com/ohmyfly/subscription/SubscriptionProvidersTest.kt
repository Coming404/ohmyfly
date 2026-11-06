// 使用 MockEngine 验证 provider 拉取、合并、递归限制与部分失败。
package com.ohmyfly.subscription

import com.ohmyfly.nativecore.NativeSubscription
import com.ohmyfly.nativecore.NativeSubscriptionProvider
import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun testProvider(
    id: String, url: String = "https://provider.example/$id", type: String = "http",
    extra: JsonObject = JsonObject(emptyMap()),
) = NativeSubscriptionProvider(id, url, buildJsonObject {
    put("type", type)
    extra.forEach { (key, value) -> put(key, value) }
})

internal fun providerSnapshot(vararg providers: NativeSubscriptionProvider): NativeSubscription =
    testSnapshot().copy(proxyProviders = providers.toList())

class SubscriptionProvidersTest {
    @Test
    fun fetchesProviderOnlySubscriptionAndPreservesHeaders() = runTest {
        val snapshots = mapOf(
            "/root" to providerSnapshot(testProvider("a", extra = buildJsonObject {
                put("header", buildJsonObject {
                    put("User-Agent", JsonArray(listOf(JsonPrimitive("provider-agent"))))
                    put("Authorization", JsonArray(listOf(JsonPrimitive("test-token"))))
                })
            })),
            "/a" to testSnapshot(testNode()),
        )
        val calls = mutableListOf<String>()
        val client = subscriptionClient { request ->
            calls += request.url.encodedPath
            if (request.url.encodedPath == "/a") {
                assertEquals("provider-agent", request.headers[HttpHeaders.UserAgent])
                assertEquals("test-token", request.headers[HttpHeaders.Authorization])
            }
            respondOk(request.url.encodedPath)
        }
        try {
            val result = SubscriptionImporter(SubscriptionFetcher(client), { snapshots.getValue(it) })
                .importSubscription("https://sub.example/root", SubscriptionSettings(userAgent = "root-agent"))
            assertNull(result.failure)
            assertEquals(1, result.nodes.size)
            assertEquals(listOf("/root", "/a"), calls)
        } finally { client.close() }
    }

    @Test
    fun expandsThreeProviderLevelsAndKeepsLastLevelNodes() = runTest {
        val calls = mutableListOf<String>()
        val root = providerSnapshot(testProvider("1"))
        val result = resolveSubscriptionProviders("https://sub.example/root", root, { error("not inline") }, { 1000 }) { url, _ ->
            val level = url.substringAfterLast('/').toInt()
            calls += url
            ParsedAttempt(parsed = testSnapshot(testNode(name = "$level", server = "s$level.example"))
                .copy(proxyProviders = listOf(testProvider("${level + 1}"))))
        }
        assertEquals(3, calls.size)
        assertEquals(listOf("1", "2", "3"), result.nodes.map { it.name })
        assertTrue(result.warnings.single().contains("3 层"))
    }

    @Test
    fun sharesThirtyTwoUrlLimitAcrossAllBranchesAndCountsFailures() = runTest {
        val root = providerSnapshot(*(1..20).map { testProvider("$it") }.toTypedArray())
        val calls = mutableListOf<String>()
        val result = resolveSubscriptionProviders("https://sub.example/root", root, { error("not inline") }, { 1000 }) { url, _ ->
            calls += url
            when (url.substringAfterLast('/')) {
                "1" -> ParsedAttempt(parsed = providerSnapshot(*(21..35).map { testProvider("$it") }.toTypedArray()))
                "2" -> ParsedAttempt(failure = "HTTP 500")
                else -> ParsedAttempt(parsed = testSnapshot(testNode(server = "s${calls.size}.example")))
            }
        }
        assertEquals(32, calls.size)
        assertEquals(32, calls.distinct().size)
        assertTrue(result.warnings.any { it.contains("32 个") })
        assertTrue(result.warnings.any { it.contains("HTTP 500") })
    }

    @Test
    fun stopsCyclesAndDuplicateUrlsWithoutDiscardingCredentials() = runTest {
        val first = testNode(extra = buildJsonObject { put("password", "one") })
        val second = testNode(extra = buildJsonObject { put("password", "two") })
        val root = testSnapshot(first).copy(proxyProviders = listOf(testProvider("a"), testProvider("again", "https://provider.example/a#same")))
        var calls = 0
        val result = resolveSubscriptionProviders("https://sub.example/root", root, { error("not inline") }, { 1000 }) { _, _ ->
            calls++
            ParsedAttempt(parsed = testSnapshot(first.copy(name = "renamed"), second).copy(
                proxyProviders = listOf(testProvider("root", "https://sub.example/root"))))
        }
        assertEquals(1, calls)
        // 展开阶段保留各来源名称，过滤后才按指纹去重。
        assertEquals(3, result.nodes.size)
        assertEquals(first.name, result.nodes.first().name)
        assertTrue(result.warnings.single().contains("循环"))
    }

    @Test
    fun keepsSuccessfulNodesOnProviderFailure() = runTest {
        val root = testSnapshot(testNode()).copy(proxyProviders = listOf(testProvider("bad")))
        val client = subscriptionClient { request ->
            if (request.url.encodedPath == "/bad") respond("missing", HttpStatusCode.NotFound) else respondOk("root")
        }
        try {
            val result = SubscriptionImporter(SubscriptionFetcher(client), { root })
                .importSubscription("https://sub.example/root", SubscriptionSettings(userAgent = "one"))
            assertNull(result.failure)
            assertEquals(1, result.nodes.size)
            assertTrue(result.warnings.single().contains("HTTP 404"))
        } finally { client.close() }
    }

    @Test
    fun filtersNamesBeforeDeduplicatingAcrossProviders() = runTest {
        val extra = buildJsonObject { put("password", "one") }
        val root = testSnapshot(testNode("剩余流量", extra = extra))
            .copy(proxyProviders = listOf(testProvider("a")))
        val child = testSnapshot(testNode("HK", extra = extra), testNode("HK copy", extra = extra),
            testNode("HK other password", extra = buildJsonObject { put("password", "two") }))
        val client = subscriptionClient { request -> respondOk(request.url.encodedPath) }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { if (it == "/root") root else child })
            val result = importer.importSubscription("https://sub.example/root", SubscriptionSettings(userAgent = "one"))
            assertNull(result.failure)
            assertEquals(listOf("HK", "HK other password"), result.nodes.map { it.name })
        } finally { client.close() }
    }

    @Test
    fun reportsFailureWhenEveryProviderFails() = runTest {
        val root = providerSnapshot(testProvider("bad"))
        val client = subscriptionClient { request ->
            if (request.url.encodedPath == "/bad") respond("missing", HttpStatusCode.NotFound) else respondOk("root")
        }
        try {
            val result = SubscriptionImporter(SubscriptionFetcher(client), { root })
                .importSubscription("https://sub.example/root", SubscriptionSettings(userAgent = "one"))
            assertNotNull(result.failure)
            assertTrue(result.warnings.single().contains("HTTP 404"))
        } finally { client.close() }
    }
}
