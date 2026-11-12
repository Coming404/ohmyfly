package com.ohmyfly.catalog

import com.ohmyfly.nativecore.NativeSubscriptionNode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

internal class CatalogRecordWriter(private val ticket: RefreshTicket, private val request: CatalogRequest = ::catalogCall) {
    private var start = 0
    private var bytes = 2
    private val batch = ArrayList<JsonElement>()
    private var references = false
    fun flush() {
        if (batch.isEmpty()) return
        request(if (references) "stageParsed" else "stage") {
            put("id", ticket.id); put("generation", ticket.generation); put("start", start)
            put(if (references) "references" else "nodes", JsonArray(batch.toList()))
        }
        start += batch.size
        batch.clear()
        bytes = 2
    }
    suspend fun append(node: NativeSubscriptionNode) {
        currentCoroutineContext().ensureActive()
        val reference = node.parsedSession != null
        if (references != reference) flush()
        references = reference
        val encoded = if (reference) buildJsonObject {
            put("session", node.parsedSession!!); put("ordinal", node.parsedOrdinal)
            put("eligible", node.eligible); put("source", node.source)
        } else catalogJson.encodeToJsonElement(NativeSubscriptionNode.serializer(), node)
        val text = encoded.toString()
        // 大节点独立分块，避免单节点受普通批次容量限制或生成整份 UTF-8 副本。
        if (text.length > 128 * 1024) {
            flush()
            val payload = if (reference) catalogJson.encodeToJsonElement(NativeSubscriptionNode.serializer(), node).toString() else text
            stageCatalogChunks(ticket, start, payload, request)
            start++
            return
        }
        val size = text.encodeToByteArray().size
        // 计入已转义JSON、UTF-8编码及元素逗号，条数由实际内容大小决定。
        if (bytes + size + (if (batch.isEmpty()) 0 else 1) > CATALOG_BATCH_BYTES) flush()
        bytes += size + if (batch.isEmpty()) 0 else 1
        batch += encoded
        // provider解析会话会在枚举完成后关闭，尾记录必须先把引用复制入刷新暂存。
        if (node.parsedLast) flush()
    }
}

// 32K UTF-16 字符即使全部需要 JSON 转义，也低于 1 MiB 请求上限。
private suspend fun stageCatalogChunks(ticket: RefreshTicket, ordinal: Int, text: String, request: CatalogRequest) {
    var index = 0
    var byteOffset = 0
    while (index < text.length) {
        currentCoroutineContext().ensureActive()
        var end = minOf(index + 32 * 1024, text.length)
        // 不拆开代理对，确保分块重组后 Unicode 与原始 JSON 完全一致。
        if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        val chunk = text.substring(index, end)
        request("chunk") {
            put("id", ticket.id); put("generation", ticket.generation); put("start", ordinal)
            put("offset", byteOffset); put("payload", chunk); put("final", end == text.length)
        }
        byteOffset += chunk.encodeToByteArray().size
        index = end
    }
}
