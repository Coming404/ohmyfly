// 按广度优先展开 provider；同一次导入共用深度、地址数量和时间预算。
package com.ohmyfly.subscription

import com.ohmyfly.nativecore.NativeSubscription
import com.ohmyfly.nativecore.NativeSubscriptionNode
import com.ohmyfly.nativecore.NativeSubscriptionProvider
import io.ktor.http.parseUrl
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal const val MaxProviderDepth = 3
internal const val MaxProviderUrls = 32

internal data class ResolvedSubscription(
    val nodes: List<NativeSubscriptionNode>, val warnings: List<String>,
)

private data class PendingProvider(
    val provider: NativeSubscriptionProvider, val depth: Int, val ancestors: Set<String>,
)

internal suspend fun resolveSubscriptionProviders(
    rootUrl: String, root: NativeSubscription, parse: (String) -> NativeSubscription,
    remainingBudget: () -> Long,
    fetch: suspend (String, Map<String, List<String>>) -> ParsedAttempt,
): ResolvedSubscription {
    val rootKey = providerUrlKey(rootUrl)
    val visited = mutableSetOf(rootKey)
    val queue = ArrayDeque<PendingProvider>()
    root.proxyProviders.forEach { queue.addLast(PendingProvider(it, 1, setOf(rootKey))) }
    val nodes = root.nodes.toMutableList()
    val warnings = mutableListOf<String>()
    var requested = 0
    while (queue.isNotEmpty()) {
        val pending = queue.removeFirst()
        if (pending.depth > MaxProviderDepth) {
            warnings += "provider 已达到 $MaxProviderDepth 层展开上限"
            continue
        }
        val provider = pending.provider
        val type = (provider.fields["type"] as? JsonPrimitive)?.content.orEmpty()
        var ancestors = pending.ancestors
        val result = if (type == "inline") {
            parseInlineProvider(provider, parse)
        } else if (type == "http" || type.isEmpty() && provider.url.isNotBlank()) {
            if (validateSubscriptionUrl(provider.url) != null) {
                warnings += "provider 地址无效，只支持 HTTP 或 HTTPS"
                continue
            }
            val key = providerUrlKey(provider.url)
            if (key in ancestors) {
                warnings += "已停止 provider 循环引用"
                continue
            }
            if (key in visited) continue
            if (requested >= MaxProviderUrls) {
                warnings += "provider 已达到 $MaxProviderUrls 个地址上限"
                continue
            }
            if (remainingBudget() <= 0) {
                warnings += "provider 请求已达到订阅总超时"
                break
            }
            visited += key
            requested++
            ancestors = ancestors + key
            fetchProvider(provider, key, fetch)
        } else {
            warnings += if (type == "file") "无法读取 provider 引用的本地文件" else "不支持该 provider 类型"
            continue
        }
        val parsed = result.parsed
        if (parsed == null) {
            warnings += "provider 获取失败：${result.failure ?: "订阅解析失败"}"
            continue
        }
        if (parsed.nodes.isEmpty() && parsed.proxyProviders.isEmpty()) {
            warnings += "provider 未解析到节点或下级引用"
        }
        nodes += parsed.nodes
        parsed.proxyProviders.forEach { queue.addLast(PendingProvider(it, pending.depth + 1, ancestors)) }
    }
    // 名称过滤完成后再去重，避免先选中信息节点而丢掉同配置的可用名称。
    return ResolvedSubscription(nodes, warnings.distinct())
}

private fun providerUrlKey(url: String): String = parseUrl(url.trim().substringBefore('#'))!!.toString()

private suspend fun fetchProvider(
    provider: NativeSubscriptionProvider, url: String,
    fetch: suspend (String, Map<String, List<String>>) -> ParsedAttempt,
): ParsedAttempt {
    val headers = try {
        providerHeaders(provider.fields)
    } catch (_: IllegalArgumentException) {
        return ParsedAttempt(failure = "请求头格式无效")
    }
    return fetch(url, headers)
}

private fun providerHeaders(fields: JsonObject): Map<String, List<String>> {
    val raw = fields["header"] ?: return emptyMap()
    require(raw is JsonObject)
    return raw.mapValues { (_, value) ->
        val entries = if (value is JsonArray) value else listOf(value)
        require(entries.isNotEmpty())
        entries.map {
            require(it is JsonPrimitive && it.isString)
            it.content
        }
    }
}

private fun parseInlineProvider(
    provider: NativeSubscriptionProvider, parse: (String) -> NativeSubscription,
): ParsedAttempt {
    val payload = provider.fields["payload"]
    if (payload !is JsonArray) return ParsedAttempt(failure = "内嵌节点格式无效")
    return try {
        ParsedAttempt(parsed = parse(JsonObject(mapOf("proxies" to payload)).toString()))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ParsedAttempt(failure = "内嵌节点解析失败")
    }
}
