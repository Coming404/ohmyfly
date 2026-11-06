// 验证 provider 接收字段不会被 Kotlin JSON 解码忽略。
package com.ohmyfly.nativecore

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeSubscriptionProvidersTest {
    @Test
    fun decodesProviderOnlyResponseAndHeaders() {
        val result = decodeNativeSubscriptionResult("""{"nodes":[],"candidates":1,"parsed":0,"skipped":1,"yaml":1,"links":0,"base64":0,"base64Layers":0,"formatCounts":{"yaml":1},"skippedByReason":{"invalid YAML item":1},"proxyProviders":[{"name":"remote","url":"https://provider.example/list","fields":{"type":"http","header":{"User-Agent":["test"]},"health-check":{"enable":true}}}]}""")
        val provider = result.proxyProviders.single()
        assertEquals("remote", provider.name)
        assertEquals("https://provider.example/list", provider.url)
        assertEquals(JsonArray(listOf(JsonPrimitive("test"))), (provider.fields["header"] as JsonObject)["User-Agent"])
        assertEquals(0, result.nodes.size)
    }
}
