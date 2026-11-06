package com.ohmyfly.nativecore

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeSubscriptionTest {
    @Test
    fun rejectsGoBridgeError() {
        val error = assertFailsWith<NativeCoreException> {
            decodeNativeSubscriptionResult("""{"error":"parse subscription: boom"}""")
        }

        assertEquals("parse subscription: boom", error.message)
    }

    @Test
    fun decodesGoBridgeOutput() {
        val result = decodeNativeSubscription(GO_BRIDGE_OUTPUT)

        val node = result.nodes.single()
        assertEquals("ss", node.type)
        assertEquals(8388, node.port)
        assertEquals(JsonPrimitive("aes-128-gcm"), node.fields["cipher"])
        assertEquals(JsonPrimitive(true), node.fields["udp"])
        assertEquals(2, result.candidates)
        assertEquals(mapOf("malformed link" to 1), result.skippedByReason)
    }
}

// native-core mobile.ParseSubscription 对一条 ss 链接和一行无效文本的实际输出。
private const val GO_BRIDGE_OUTPUT =
    """{"nodes":[{"name":"HK","type":"ss","server":"example.com","port":8388,"fields":{"cipher":"aes-128-gcm","password":"pass","port":8388,"server":"example.com","type":"ss","udp":true},"sourceFormat":"link","fromBase64":false,"fingerprint":"6cdf00db4ae6d4fe2117e562fe7227cc9503dbbde5b924651d1a6c0969dbdb66"}],"candidates":2,"parsed":1,"skipped":1,"yaml":0,"links":1,"base64":0,"base64Layers":0,"formatCounts":{"link":1},"skippedByReason":{"malformed link":1},"proxyProviders":[]}"""
