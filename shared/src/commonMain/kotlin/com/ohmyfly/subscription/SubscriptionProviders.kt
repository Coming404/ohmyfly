// 按广度优先展开 provider；同一次导入共用深度、地址数量和时间预算。
package com.ohmyfly.subscription

import com.ohmyfly.logging.logger
import com.ohmyfly.logging.logTarget

import com.ohmyfly.nativecore.NativeSubscription
import com.ohmyfly.nativecore.NativeSubscriptionNode
import com.ohmyfly.nativecore.NativeSubscriptionProvider
import com.ohmyfly.nativecore.close
import kotlinx.serialization.Serializable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

@Serializable
internal data class PendingProvider(
    val provider: NativeSubscriptionProvider, val depth: Int, val ancestors: Set<String>,
)

internal interface ProviderQueue {
    val size: Int
    fun add(value: PendingProvider)
    fun remove(): PendingProvider
    fun close()
}

internal class MemoryProviderQueue : ProviderQueue {
    private val values = ArrayDeque<PendingProvider>()
    private var bytes = 0L
    override val size: Int get() = values.size
    override fun add(value: PendingProvider) {
        val size = value.provider.fields.toString().length.toLong() * 2
        require(values.size < 256 && bytes + size <= 8 * 1024 * 1024) { "provider 队列超过预算" }
        values.addLast(value); bytes += size
    }
    override fun remove(): PendingProvider = values.removeFirst().also { bytes -= it.provider.fields.toString().length.toLong() * 2 }
    override fun close() { values.clear(); bytes = 0 }
}

internal suspend fun resolveSubscriptionProviders(
    rootUrl: String, root: NativeSubscription, parse: suspend (String) -> NativeSubscription,
    remainingBudget: () -> Long,
    operation: String = "",
    queue: ProviderQueue = MemoryProviderQueue(),
    accept: (suspend (NativeSubscriptionNode) -> Unit)? = null,
    fetch: suspend (String, Map<String, List<String>>) -> ParsedAttempt,
): ResolvedSubscription {
    val rootKey = providerUrlKey(rootUrl)
    val visited = mutableSetOf(rootKey)
    val nodes = mutableListOf<NativeSubscriptionNode>()
    var emitted = 0
    suspend fun emit(node: NativeSubscriptionNode) {
        currentCoroutineContext().ensureActive()
        emitted++
        if (accept == null) nodes += node else accept(node)
    }
    val warnings = linkedSetOf<String>()
    var requested = 0
    try {
    root.proxyProviders.forEach { queue.add(PendingProvider(it, 1, setOf(rootKey))) }
    root.nodes.forEach { emit(it.copy(source = JsonObject(mapOf("url" to JsonPrimitive(rootUrl))))) }
    while (queue.size > 0) {
        currentCoroutineContext().ensureActive()
        val pending = queue.remove()
        logger.info { "[订阅 $operation] 展开子订阅引用 ${logTarget(pending.provider.url)}，当前为第 ${pending.depth} 层，最多展开 $MaxProviderDepth 层；还剩 ${queue.size} 个引用，已请求 $requested 个地址，上限为 $MaxProviderUrls 个" }
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
            if (key in visited) {
                logger.info { "[订阅 $operation] 子订阅地址已经请求过，跳过重复引用" }
                continue
            }
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
        if (parsed.nodes.isEmpty() && parsed.proxyProviders.isEmpty()) warnings += "provider 未解析到节点或下级引用"
        expandParsedProvider(parsed, provider, rootUrl, pending.depth, ancestors, queue, operation, ::emit)
    }
    } finally { queue.close() }
    // 名称过滤完成后再去重，避免先选中信息节点而丢掉同配置的可用名称。
    warnings.distinct().forEach { reason -> logger.info { "[订阅 $operation] 子订阅提示：${reason.replace("provider", "子订阅")}" } }
    logger.info { "[订阅 $operation] 获取 1 个根来源、$requested 个子订阅地址，共 $emitted 个航班，产生 ${warnings.distinct().size} 条提示" }
    return ResolvedSubscription(nodes, warnings.distinct())
}

private suspend fun expandParsedProvider(
    parsed: NativeSubscription, provider: NativeSubscriptionProvider, rootUrl: String,
    depth: Int, ancestors: Set<String>, queue: ProviderQueue, operation: String,
    emit: suspend (NativeSubscriptionNode) -> Unit,
) {
    try {
        logger.info { "[订阅 $operation] 子订阅解析完成，新增 ${parsed.nodes.size} 个节点，发现 ${parsed.proxyProviders.size} 个下级引用，发生 ${parsed.skipped} 次跳过" }
        if (parsed.skippedByReason.isNotEmpty()) logger.info {
            "[订阅 $operation] 子订阅跳过原因：" + parsed.skippedByReason.entries.joinToString("；") { (reason, count) ->
                "${subscriptionSkipDescription(reason)}，共 $count 次"
            }
        }
        parsed.nodes.forEach { node -> emit(providerNodeSource(node, provider, rootUrl)) }
        parsed.proxyProviders.forEach { queue.add(PendingProvider(it, depth + 1, ancestors)) }
    } finally { parsed.close() }
}

private fun providerNodeSource(
    node: NativeSubscriptionNode, provider: NativeSubscriptionProvider, rootUrl: String,
): NativeSubscriptionNode = node.copy(source = JsonObject(mapOf(
    "url" to JsonPrimitive(provider.url), "provider" to JsonPrimitive(provider.name),
    "fields" to JsonObject(provider.fields - "payload"), "root" to JsonPrimitive(rootUrl),
)))

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

private suspend fun parseInlineProvider(
    provider: NativeSubscriptionProvider, parse: suspend (String) -> NativeSubscription,
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
