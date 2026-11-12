package com.ohmyfly.catalog

import com.ohmyfly.nativecore.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

internal suspend fun parseCatalogSubscription(content: String): NativeSubscription {
    val id = catalogCall("parseBegin").getValue("id").jsonPrimitive.long
    try {
        var offset = 0
        var ordinal = 0
        while (offset < content.length) {
            currentCoroutineContext().ensureActive()
            var end = minOf(offset + 32 * 1024, content.length)
            if (end < content.length && content[end - 1].isHighSurrogate() && content[end].isLowSurrogate()) end--
            catalogCall("parseChunk") { put("id", id); put("start", ordinal++); put("payload", content.substring(offset, end)) }
            offset = end
        }
        currentCoroutineContext().ensureActive()
        val result = catalogCall("parseFinish") { put("id", id) }
        currentCoroutineContext().ensureActive()
        val parsed = catalogJson.decodeFromJsonElement<NativeSubscription>(result.getValue("subscription"))
        return parsed.copy(
            nodes = ParsedNodes(id, result.getValue("nodeCount").jsonPrimitive.int),
            proxyProviders = ParsedProviders(id, result.getValue("providerCount").jsonPrimitive.int),
        )
    } catch (e: Exception) {
            try { catalogCall("parseEnd") { put("id", id) } }
            catch (cleanup: Exception) { e.addSuppressed(cleanup) }
        throw e
    }
}

private class ParsedNodes(private val session: Long, override val size: Int) :
    AbstractList<NativeSubscriptionNode>(), NativeSubscriptionResource {
    private var first = 0
    private var page = emptyList<NativeSubscriptionNode>()
    override fun get(index: Int): NativeSubscriptionNode {
        require(index in 0 until size)
        if (index !in first until first + page.size) {
            first = index
            page = catalogJson.decodeFromJsonElement(catalogCall("parsePage") {
                put("id", session); put("kind", "node"); put("start", index)
            }.getValue("records"))
        }
        return page[index - first].copy(parsedSession = session, parsedOrdinal = index, parsedLast = index == size - 1)
    }
    override fun close() { page = emptyList(); catalogCall("parseEnd") { put("id", session) } }
}

private class ParsedProviders(private val session: Long, override val size: Int) : AbstractList<NativeSubscriptionProvider>() {
    override fun get(index: Int): NativeSubscriptionProvider = catalogJson.decodeFromJsonElement(
        catalogCall("parseRecord") { put("id", session); put("kind", "provider"); put("start", index) })
}
