package com.ohmyfly.subscription

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionSnapshotsTest {
    @Test
    fun nodeSupersetWinsAndIsNotMergedWithOtherProfiles() = runTest {
        val a = testNode("A", server = "a.example")
        val b = testNode("B", server = "b.example")
        val result = importSnapshots(testSnapshot(a), testSnapshot(a, b))
        assertNull(result.failure)
        assertEquals(listOf("A", "B"), result.nodes.map { it.name })
    }

    @Test
    fun laterSubsetCannotReplaceTheMoreCompleteSnapshot() = runTest {
        val a = testNode("A", server = "a.example")
        val b = testNode("B", server = "b.example")
        val result = importSnapshots(testSnapshot(a, b, skipped = 2), testSnapshot(a))
        assertEquals(listOf("A", "B"), result.nodes.map { it.name })
    }

    @Test
    fun nestedFieldSupersetWinsForTheSameEndpoint() = runTest {
        val rich = testNode("rich", extra = Json.parseToJsonElement("""{"ws-opts":{"headers":{"Host":"cdn.example"},"paths":["/ws"]}}""").let { it as kotlinx.serialization.json.JsonObject })
        val result = importSnapshots(testSnapshot(testNode("plain")), testSnapshot(rich))
        assertEquals(listOf("rich"), result.nodes.map { it.name })
    }

    @Test
    fun equivalentSnapshotWithFewerSkippedItemsWins() = runTest {
        val result = importSnapshots(testSnapshot(testNode("old"), skipped = 1), testSnapshot(testNode("new")))
        assertEquals(listOf("new"), result.nodes.map { it.name })
    }

    @Test
    fun renamedDuplicatesDoNotMakeASnapshotMoreComplete() = runTest {
        val result = importSnapshots(testSnapshot(testNode("original")), testSnapshot(testNode("copy-1"), testNode("copy-2")))
        assertEquals(listOf("original"), result.nodes.map { it.name })
    }

    @Test
    fun divergentSnapshotsPreferMoreProtocolsAfterNodeCount() = runTest {
        val result = importSnapshots(
            testSnapshot(testNode("SS1", server = "a.example"), testNode("SS2", server = "b.example")),
            testSnapshot(testNode("SS", server = "a.example"), testNode("Trojan", type = "trojan", server = "c.example")),
        )
        assertEquals(listOf("ss", "trojan"), result.nodes.map { it.type })
    }

    @Test
    fun credentialConflictsWithEqualRankKeepTheFirstSnapshot() = runTest {
        val first = testNode("first", extra = Json.parseToJsonElement("""{"password":"one"}""") as kotlinx.serialization.json.JsonObject)
        val next = testNode("next", extra = Json.parseToJsonElement("""{"password":"two"}""") as kotlinx.serialization.json.JsonObject)
        assertEquals(listOf("first"), importSnapshots(testSnapshot(first), testSnapshot(next)).nodes.map { it.name })
    }

    @Test
    fun infoNodesDoNotOutrankUsableNodeCompleteness() = runTest {
        val result = importSnapshots(
            testSnapshot(testNode("A", server = "a.example"), testNode("剩余流量：10 GB"), testNode("会员 | 黄金")),
            testSnapshot(testNode("A", server = "a.example"), testNode("B", server = "b.example")),
        )
        assertEquals(listOf("A", "B"), result.nodes.map { it.name })
    }

    @Test
    fun metadataOnlyProfilesAreNotUsableSubscriptions() = runTest {
        val result = importSnapshots(testSnapshot(testNode("剩余订阅流量: 10 GB"), testNode("请更新客户端")))
        assertEquals("未解析到有效节点", result.failure)
        assertEquals(emptyList(), result.nodes)
    }

    @Test
    fun clientUpdatePlaceholderReturnsTheUpstreamFailureText() = runTest {
        val result = importSnapshots(testSnapshot(testNode("v2rayN update", server = "LOCALHOST.", port = 1)))
        assertEquals("订阅服务返回了客户端更新提示占位内容，未导入该结果", result.failure)
    }

    @Test
    fun usableSnapshotWinsOverAClientUpdatePlaceholder() = runTest {
        val result = importSnapshots(
            testSnapshot(testNode("v2rayN update", server = "127.0.0.1", port = 0)),
            testSnapshot(testNode("usable")),
        )
        assertNull(result.failure)
        assertEquals(listOf("usable"), result.nodes.map { it.name })
    }

    @Test
    fun coverageRequiresDistinctCounterparts() = runTest {
        val x = Json.parseToJsonElement("""{"x":1}""") as kotlinx.serialization.json.JsonObject
        val y = Json.parseToJsonElement("""{"y":1}""") as kotlinx.serialization.json.JsonObject
        val xy = Json.parseToJsonElement("""{"x":1,"y":1}""") as kotlinx.serialization.json.JsonObject
        val result = importSnapshots(
            testSnapshot(testNode("x", extra = x), testNode("y", extra = y)),
            testSnapshot(testNode("xy", extra = xy), testNode("bare"), skipped = 1),
        )
        assertEquals(listOf("x", "y"), result.nodes.map { it.name })
    }

    @Test
    fun displayNameAndInternalFieldsDoNotImproveCompleteness() = runTest {
        val extra = Json.parseToJsonElement("""{"name":"renamed","_internal":{"value":1}}""") as kotlinx.serialization.json.JsonObject
        val result = importSnapshots(testSnapshot(testNode("first")), testSnapshot(testNode("next", extra = extra)))
        assertEquals(listOf("first"), result.nodes.map { it.name })
    }
}
