// 覆盖真机失败的桥接票据及用户输入分流，防止集合空值再次阻断所有导入。
package com.ohmyfly.catalog

import com.ohmyfly.settings.SubscriptionSettings
import com.ohmyfly.ui.TowerImportDraft
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogImportContractTest {
    @Test
    fun refreshTicketDoesNotDecodeUnusedDeduplicationCollections() {
        // 旧核心真实返回的 null 规则集合由 ignoreUnknownKeys 忽略，客户端只消费票据身份。
        val ticket = catalogJson.decodeFromString<RefreshTicket>(
            """{"id":1,"generation":2,"address":"local:text:test","name":"","dedupRules":{"mode":"none","commonFields":null,"protocolRules":null}}""",
        )
        assertEquals(RefreshTicket(1, 2, "local:text:test", ""), ticket)
    }

    @Test
    fun linksInputRoutesSubscriptionsAndNodeProtocolsToTheirActualImportPaths() = runTest {
        val draft = TowerImportDraft(null)
        val node = "trojan://test@node.example:443#HK"
        draft.urls = "https://sub.example/profile\n$node\n$node"
        val inputs = draft.inputs(SubscriptionSettings(), null)
        assertEquals(2, inputs.size)
        assertEquals("https://sub.example/profile", inputs[0].address)
        assertTrue(inputs[1].address.startsWith("local:text:"))
        assertEquals(node, assertNotNull(inputs[1].read).invoke().content)
    }

    @Test
    fun serializationFailureReportsFieldWithoutExposingJsonBody() {
        val error = try {
            catalogJson.decodeFromString<DeduplicationRule>("""{"commonFields":null,"token":"secret-value"}""")
            error("需触发集合解码失败")
        } catch (error: kotlinx.serialization.SerializationException) { error }
        val reason = catalogFailure(error)
        assertTrue(reason.startsWith("塔台与航班数据格式不匹配"))
        assertTrue("secret-value" !in reason)
        assertTrue("JSON input" !in reason)
    }
}
