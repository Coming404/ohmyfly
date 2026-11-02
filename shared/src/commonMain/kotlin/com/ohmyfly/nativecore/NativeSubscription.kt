package com.ohmyfly.nativecore

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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
)

private val nativeJson = Json { ignoreUnknownKeys = true }

/** 解码 Go 桥接 ParseSubscription 返回的 JSON。 */
internal fun decodeNativeSubscription(json: String): NativeSubscription =
    nativeJson.decodeFromString(NativeSubscription.serializer(), json)
