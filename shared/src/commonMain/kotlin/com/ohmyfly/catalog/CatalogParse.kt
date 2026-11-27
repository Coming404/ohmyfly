package com.ohmyfly.catalog

import com.ohmyfly.nativecore.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

internal suspend fun parseCatalogSubscription(content: String, request: CatalogRequest = ::catalogCall): NativeSubscription {
    val id = request("parseBegin") {}.getValue("id").jsonPrimitive.long
    try {
        var offset = 0
        var ordinal = 0
        while (offset < content.length) {
            currentCoroutineContext().ensureActive()
            var end = minOf(offset + 32 * 1024, content.length)
            if (end < content.length && content[end - 1].isHighSurrogate() && content[end].isLowSurrogate()) end--
            request("parseChunk") { put("id", id); put("start", ordinal++); put("payload", content.substring(offset, end)) }
            offset = end
        }
        currentCoroutineContext().ensureActive()
        val result = request("parseFinish") { put("id", id) }
        currentCoroutineContext().ensureActive()
        val parsed = catalogJson.decodeFromJsonElement<NativeSubscription>(result.getValue("subscription"))
        return parsed.copy(
            nodes = ParsedNodes(id, result.getValue("nodeCount").jsonPrimitive.int, request),
            proxyProviders = ParsedProviders(id, result.getValue("providerCount").jsonPrimitive.int, request),
        )
    } catch (e: Exception) {
            try { request("parseEnd") { put("id", id) } }
            catch (cleanup: Exception) { e.addSuppressed(cleanup) }
        throw e
    }
}

private class ParsedNodes(private val session: Long, override val size: Int, private val request: CatalogRequest) :
    AbstractList<NativeSubscriptionNode>(), NativeSubscriptionResource {
    private var first = 0
    private var page = emptyList<NativeSubscriptionNode>()
    override fun get(index: Int): NativeSubscriptionNode {
        require(index in 0 until size)
        if (index !in first until first + page.size && index != first + page.size) {
            // 覆盖比较会随机回看旧记录；只取目标节点，避免每次重解码近 900 KB 的整页。
            return catalogJson.decodeFromJsonElement<NativeSubscriptionNode>(request("parseRecord") {
                put("id", session); put("kind", "node"); put("start", index)
            }).copy(parsedSession = session, parsedOrdinal = index, parsedLast = index == size - 1)
        }
        if (index !in first until first + page.size) {
            first = index
            page = catalogJson.decodeFromJsonElement(request("parsePage") {
                put("id", session); put("kind", "node"); put("start", index)
            }.getValue("records"))
        }
        return page[index - first].copy(parsedSession = session, parsedOrdinal = index, parsedLast = index == size - 1)
    }
    override fun close() { page = emptyList(); request("parseEnd") { put("id", session) } }
}

private class ParsedProviders(private val session: Long, override val size: Int, private val request: CatalogRequest) : AbstractList<NativeSubscriptionProvider>() {
    override fun get(index: Int): NativeSubscriptionProvider = catalogJson.decodeFromJsonElement(
        request("parseRecord") { put("id", session); put("kind", "provider"); put("start", index) })
}
