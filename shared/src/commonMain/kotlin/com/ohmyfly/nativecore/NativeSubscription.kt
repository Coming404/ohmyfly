// 接收 Go 输出的节点与 provider，指纹只用于本次导入的内存去重。
package com.ohmyfly.nativecore

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class NativeSubscription(
    val nodes: List<NativeSubscriptionNode>,
    val candidates: Int,
    val parsed: Int,
    val skipped: Int,
    val yaml: Int,
    val links: Int,
    val base64: Int,
    val base64Layers: Int,
    val formatCounts: Map<String, Int>,
    val skippedByReason: Map<String, Int>,
    val proxyProviders: List<NativeSubscriptionProvider> = emptyList(),
)

@Serializable
data class NativeSubscriptionProvider(
    val name: String,
    val url: String,
    val fields: JsonObject,
)

@Serializable
data class NativeSubscriptionNode(
    val name: String,
    val type: String,
    val server: String,
    val port: Int,
    val fields: JsonObject,
    val sourceFormat: String,
    val fromBase64: Boolean,
    val fingerprint: String,
)

class NativeCoreException(message: String) : Exception(message)

private val nativeJson = Json { ignoreUnknownKeys = true }

/** 解码 Go 桥接 ParseSubscription 返回的 JSON。 */
internal fun decodeNativeSubscription(json: String): NativeSubscription =
    nativeJson.decodeFromString(NativeSubscription.serializer(), json)

/** 解码桥接返回值；含 error 字段时抛出 NativeCoreException。 */
internal fun decodeNativeSubscriptionResult(json: String): NativeSubscription {
    val result = nativeJson.parseToJsonElement(json).jsonObject
    result["error"]?.let { throw NativeCoreException(it.jsonPrimitive.content) }
    return nativeJson.decodeFromJsonElement(NativeSubscription.serializer(), result)
}
