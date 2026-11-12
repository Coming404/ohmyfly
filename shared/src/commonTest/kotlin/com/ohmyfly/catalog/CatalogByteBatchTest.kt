package com.ohmyfly.catalog

import com.ohmyfly.nativecore.NativeSubscriptionNode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

// 覆盖实际分批器的转义、多字节、顺序与节点序号，不能用条数猜测JSON大小。
class CatalogByteBatchTest {
    @Test
    fun shortSourcesStayTogetherBeyondOldLimits() {
        val inputs = List(257) { CatalogImportSource("https://source$it.example/sub", "来源 $it") }
        val batches = sourceBatches(inputs).toList()
        assertEquals(1, batches.size)
        assertEquals(inputs, batches.single().inputs)
        assertEquals(257, batches.single().records.size)
    }

    @Test
    fun sourcesSplitByEncodedBytesWithoutLosingOrder() {
        val inputs = List(220) { CatalogImportSource("https://example.com/$it?value=" + "中".repeat(1800), "名称\"\\\n$it") }
        val batches = sourceBatches(inputs).toList()
        assertTrue(batches.size > 1)
        assertEquals(inputs, batches.flatMap { it.inputs })
        batches.forEach { batch ->
            assertTrue(batch.records.toString().encodeToByteArray().size <= CATALOG_BATCH_BYTES)
            assertEquals(batch.inputs.map { it.address }, batch.records.map { it.jsonObject.getValue("address").jsonPrimitive.content })
            assertEquals(batch.inputs.map { it.name }, batch.records.map { it.jsonObject.getValue("name").jsonPrimitive.content })
        }
    }

    @Test
    fun smallNodesUseOneWriteBeyondHundred() = runTest {
        val writes = mutableListOf<JsonObject>()
        val writer = CatalogRecordWriter(RefreshTicket(1, 1, "", "")) { action, body ->
            writes += buildJsonObject { put("action", action); body() }; buildJsonObject {}
        }
        repeat(257) { writer.append(node("节点$it")) }
        writer.flush()
        assertEquals(1, writes.size)
        assertEquals(257, writes.single().getValue("nodes").jsonArray.size)
    }

    @Test
    fun nodeBytesIncludeEscapingAndPreserveOrdinals() = runTest {
        val writes = mutableListOf<JsonObject>()
        val writer = CatalogRecordWriter(RefreshTicket(1, 1, "", "")) { action, body ->
            writes += buildJsonObject { put("action", action); body() }; buildJsonObject {}
        }
        val names = List(240) { "节点\"\\\n$it" + "中".repeat(1800) }
        names.forEach { writer.append(node(it)) }
        writer.flush()
        assertTrue(writes.size > 1)
        var ordinal = 0
        writes.forEach {
            assertEquals("stage", it.getValue("action").jsonPrimitive.content)
            assertEquals(ordinal, it.getValue("start").jsonPrimitive.int)
            val nodes = it.getValue("nodes").jsonArray
            assertTrue(nodes.toString().encodeToByteArray().size <= CATALOG_BATCH_BYTES)
            assertTrue(it.toString().encodeToByteArray().size <= 1024 * 1024)
            ordinal += nodes.size
        }
        assertEquals(names, writes.flatMap { it.getValue("nodes").jsonArray }.map { it.jsonObject.getValue("name").jsonPrimitive.content })
    }

    // 会话尾记录到达时必须同步复制引用，调用方随后即可关闭Go解析会话。
    @Test
    fun parsedTailFlushesReferencesBeforeSessionCloses() = runTest {
        val writes = mutableListOf<JsonObject>()
        val writer = CatalogRecordWriter(RefreshTicket(1, 1, "", "")) { action, body ->
            writes += buildJsonObject { put("action", action); body() }; buildJsonObject {}
        }
        val parsed = node("大节点").copy(rawInput = "x".repeat(150_000), parsedSession = 7)
        writer.append(parsed.copy(eligible = false))
        assertTrue(writes.isEmpty())
        writer.append(parsed.copy(parsedOrdinal = 1, parsedLast = true))
        assertEquals(1, writes.size)
        val batch = writes.single()
        assertEquals("stageParsed", batch.getValue("action").jsonPrimitive.content)
        val refs = batch.getValue("references").jsonArray
        assertEquals(listOf(0, 1), refs.map { it.jsonObject.getValue("ordinal").jsonPrimitive.int })
        assertFalse(refs.first().jsonObject.getValue("eligible").jsonPrimitive.boolean)
        assertTrue(refs.all { "fields" !in it.jsonObject && "rawInput" !in it.jsonObject })
        writer.append(node("外部节点"))
        writer.flush()
        assertEquals("stage", writes.last().getValue("action").jsonPrimitive.content)
        assertEquals(2, writes.last().getValue("start").jsonPrimitive.int)
        val serialized = catalogJson.encodeToJsonElement(NativeSubscriptionNode.serializer(), parsed).jsonObject
        assertFalse("parsedSession" in serialized)
        assertFalse("parsedOrdinal" in serialized)
        assertFalse("parsedLast" in serialized)
    }

    private fun node(name: String) = NativeSubscriptionNode(name, "trojan", "node.example", 443,
        buildJsonObject { put("password", "fixture") }, "links", false, "fixture")
}
