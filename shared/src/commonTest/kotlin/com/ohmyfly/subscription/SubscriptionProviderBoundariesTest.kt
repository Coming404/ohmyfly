// 验证 provider 输入边界、请求预算和取消传播。
package com.ohmyfly.subscription

import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SubscriptionProviderBoundariesTest {
    @Test
    fun parsesInlinePayloadWithoutRequestingAndReportsLocalFile() = runTest {
        val root = providerSnapshot(
            testProvider("inline", url = "", type = "inline", extra = buildJsonObject {
                put("payload", JsonArray(listOf(buildJsonObject { put("type", "ss") })))
            }), testProvider("file", url = "", type = "file"),
        )
        var parsed = ""
        val result = resolveSubscriptionProviders("https://sub.example/root", root, {
            parsed = it
            testSnapshot(testNode())
        }, { 1000 }) { _, _ -> error("must not request") }
        assertEquals("""{"proxies":[{"type":"ss"}]}""", parsed)
        assertEquals(1, result.nodes.size)
        assertTrue(result.warnings.single().contains("本地文件"))
    }

    @Test
    fun rejectsInvalidAddressesAndHeadersBeforeRequest() = runTest {
        val root = providerSnapshot(
            testProvider("ftp", url = "ftp://provider.example/list"),
            testProvider("badHeader", extra = buildJsonObject { put("header", JsonPrimitive(123)) }),
            testProvider("emptyAgent", extra = buildJsonObject {
                put("header", JsonObject(mapOf("User-Agent" to JsonArray(emptyList()))))
            }),
        )
        val result = resolveSubscriptionProviders("https://sub.example/root", root, { error("not inline") }, { 1000 }) { _, _ ->
            error("invalid boundary must not request")
        }
        assertTrue(result.nodes.isEmpty())
        assertTrue(result.warnings.any { it.contains("地址无效") })
        assertTrue(result.warnings.any { it.contains("请求头格式无效") })
    }

    @Test
    fun emptyProviderResponseIsReportedWithoutDroppingRootNodes() = runTest {
        val root = testSnapshot(testNode()).copy(proxyProviders = listOf(testProvider("empty")))
        val result = resolveSubscriptionProviders("https://sub.example/root", root, { error("not inline") }, { 1000 }) { _, _ ->
            ParsedAttempt(parsed = testSnapshot())
        }
        assertEquals(1, result.nodes.size)
        assertTrue(result.warnings.single().contains("未解析到节点"))
    }

    @Test
    fun keepsCollectedNodesWhenTotalBudgetExpires() = runTest {
        val root = providerSnapshot(testProvider("a"), testProvider("b"))
        var remaining = 1000L
        var calls = 0
        val result = resolveSubscriptionProviders("https://sub.example/root", root, { error("not inline") }, { remaining }) { _, _ ->
            calls++
            remaining = 0
            ParsedAttempt(parsed = testSnapshot(testNode()))
        }
        assertEquals(1, calls)
        assertEquals(1, result.nodes.size)
        assertTrue(result.warnings.single().contains("总超时"))
    }

    @Test
    fun propagatesCancellationInsteadOfReturningPartialSuccess() = runTest {
        assertFailsWith<CancellationException> {
            resolveSubscriptionProviders("https://sub.example/root", providerSnapshot(testProvider("a")),
                { error("not inline") }, { 1000 }) { _, _ -> throw CancellationException("cancel") }
        }
    }

    @Test
    fun towerPublishesPartialFailureWithoutThrowingAwaySuccessfulNodes() = runTest {
        val root = testSnapshot(testNode()).copy(proxyProviders = listOf(testProvider("file", "", "file")))
        val client = subscriptionClient { respondOk("root") }
        try {
            val store = SubscriptionTowerStore(SubscriptionImporter(SubscriptionFetcher(client), { root }))
            store.add("https://sub.example/root", SubscriptionSettings(userAgent = "one"))
            val tower = store.towers.value.single()
            assertEquals(1, tower.nodes.size)
            assertEquals(null, tower.failure)
            assertTrue(tower.warnings.single().contains("本地文件"))
        } finally { client.close() }
    }
}
