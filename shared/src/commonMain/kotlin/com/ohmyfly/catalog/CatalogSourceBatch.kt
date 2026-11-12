package com.ohmyfly.catalog

import kotlinx.serialization.json.*

// 数组按编码后的UTF-8字节计费，给1MiB请求中的动作、名称和事务字段留出空间。
internal const val CATALOG_BATCH_BYTES = 900_000

internal data class CatalogSourceBatch(val inputs: List<CatalogImportSource>, val records: JsonArray)

// 按需编码下一批，保留原来源顺序与读取动作，不为全部来源预建JSON副本。
internal fun sourceBatches(inputs: List<CatalogImportSource>): Sequence<CatalogSourceBatch> = sequence {
    var start = 0
    var bytes = 2 // 数组首尾方括号。
    var records = mutableListOf<JsonElement>()
    for ((index, input) in inputs.withIndex()) {
        val encoded = buildJsonObject { put("address", input.address); put("name", input.name) }
        val size = encoded.toString().encodeToByteArray().size
        require(size + 2 <= CATALOG_BATCH_BYTES) { "单个来源信息超过桥接字节预算" }
        if (bytes + size + (if (records.isEmpty()) 0 else 1) > CATALOG_BATCH_BYTES) {
            yield(CatalogSourceBatch(inputs.subList(start, index), JsonArray(records)))
            start = index
            bytes = 2
            records = mutableListOf()
        }
        bytes += size + if (records.isEmpty()) 0 else 1
        records += encoded
    }
    if (records.isNotEmpty()) yield(CatalogSourceBatch(inputs.subList(start, inputs.size), JsonArray(records)))
}
