package com.ohmyfly.catalog

import com.ohmyfly.subscription.PendingProvider
import com.ohmyfly.subscription.ProviderQueue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

// 队列正文全部放 SQLite；内存只保留两个游标和当前一个 provider。
internal class CatalogProviderQueue : ProviderQueue {
    private val id = catalogCall("parseBegin").getValue("id").jsonPrimitive.long
    private var head = 0
    private var tail = 0
    override val size: Int get() = tail - head

    override fun add(value: PendingProvider) {
        val text = catalogJson.encodeToString(value)
        var offset = 0
        var chunk = 0
        while (offset < text.length) {
            var end = minOf(offset + 32 * 1024, text.length)
            if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
            catalogCall("parseChunk") { put("id", id); put("start", chunk++); put("payload", text.substring(offset, end)) }
            offset = end
        }
        catalogCall("queuePush") { put("id", id); put("start", tail++) }
    }

    override fun remove(): PendingProvider = catalogJson.decodeFromJsonElement(
        catalogCall("queuePop") { put("id", id); put("start", head++) })

    override fun close() { catalogCall("parseEnd") { put("id", id) } }
}
