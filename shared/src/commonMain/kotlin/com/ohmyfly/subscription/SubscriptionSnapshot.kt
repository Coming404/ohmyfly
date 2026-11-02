// Ported from SubBoost local/src/lib/snapshot-comparison.ts @401534742802e2b1542783197ce66451bad82739 (AGPL-3.0-only).
// Ported from SubBoost packages/core/src/{subscription/info-node-name,parser/placeholder}.ts at the same commit (AGPL-3.0-only).
package com.ohmyfly.subscription

import com.ohmyfly.nativecore.NativeSubscription
import com.ohmyfly.nativecore.NativeSubscriptionNode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val infoName = Regex("^(剩余流量|剩余订阅流量|距离下次重置剩余|套餐到期|订阅到期|到期时间|过期时间)\\s*[:：]|^(余额|会员)\\s*\\||版本太旧|请更新|更新软件|去官网更新")
private val updateHint = Regex("v2rayn|clash|版本|太旧|过旧|更新|upgrade|update", RegexOption.IGNORE_CASE)
private val protocolName = Regex("^[a-z][a-z0-9-]{0,31}$")

internal fun looksLikeClientUpdatePlaceholderNodes(nodes: List<NativeSubscriptionNode>): Boolean {
    if (nodes.size != 1) return false
    val node = nodes.single()
    val host = node.server.trim().lowercase().removeSuffix(".")
    return host in setOf("127.0.0.1", "localhost", "0.0.0.0", "::1", "[::1]") &&
        node.port <= 1 && updateHint.containsMatchIn(node.name.trim())
}

internal fun hasUsableSubscriptionSnapshot(parsed: NativeSubscription): Boolean =
    !looksLikeClientUpdatePlaceholderNodes(parsed.nodes) && parsed.nodes.any { !infoName.containsMatchIn(it.name.trim()) }

private data class NodeAnchor(val type: String, val server: String, val port: Int)
private data class NodeFields(val anchor: NodeAnchor, val fields: Map<String, String>)
private data class Snapshot(val nodes: List<NodeFields>, val protocols: Int, val fields: Int, val errors: Int)

private fun nodeFields(node: NativeSubscriptionNode): NodeFields? {
    if (infoName.containsMatchIn(node.name.trim())) return null
    val fields = mutableMapOf<String, String>()
    fun visit(value: JsonElement, path: String) {
        when (value) {
            JsonNull -> Unit
            is JsonObject -> value.forEach { (key, child) ->
                if (!key.startsWith('_') && (path.isNotEmpty() || key != "name")) visit(child, "$path/$key")
            }
            is JsonArray -> value.forEachIndexed { index, child -> visit(child, "$path/$index") }
            is JsonPrimitive -> if (value.content.isNotEmpty()) fields[path] = value.toString()
        }
    }
    visit(node.fields, "")
    // shortcut: DTO 无 mihomo sanitizer，anchor 仅 type/server/port；有共享 sanitizer 后再对齐 peers/port-range。
    return NodeFields(NodeAnchor(node.type, node.server, node.port), fields)
}

private fun summarize(parsed: NativeSubscription): Snapshot {
    val candidates = if (looksLikeClientUpdatePlaceholderNodes(parsed.nodes)) emptyList() else parsed.nodes
    val records = candidates.mapNotNull(::nodeFields).distinctBy { it.fields }
    val protocols = candidates.filter { !infoName.containsMatchIn(it.name.trim()) }
        .map { if (protocolName.matches(it.type)) it.type else "unknown" }.distinct().size
    // shortcut: DTO 只提供 skipped 统计，以其近似上游 errors.length；DTO 支持逐条解析错误后再对齐。
    return Snapshot(records, protocols, records.sumOf { it.fields.size }, parsed.skipped)
}

private fun isSubset(left: NodeFields, right: NodeFields): Boolean =
    left.fields.all { (path, value) -> right.fields[path] == value }

private fun covers(left: Snapshot, right: Snapshot): Boolean {
    if (left.nodes.size > right.nodes.size) return false
    val groups = right.nodes.groupBy { it.anchor }
        .mapValues { (_, nodes) -> nodes.sortedBy { it.fields.size }.toMutableList() }
    var comparisons = 0
    for (node in left.nodes.sortedByDescending { it.fields.size }) {
        val group = groups[node.anchor] ?: return false
        val index = group.indexOfFirst { ++comparisons <= 100_000 && isSubset(node, it) }
        if (index < 0 || comparisons > 100_000) return false
        group.removeAt(index)
    }
    return true
}

internal fun isBetterSubscriptionSnapshot(current: NativeSubscription, next: NativeSubscription): Boolean {
    val a = summarize(current)
    val b = summarize(next)
    val aCovered = covers(a, b)
    val bCovered = covers(b, a)
    if (aCovered && !bCovered) return true
    if (bCovered) return aCovered && b.errors < a.errors
    // 上游 divergent/field-conflict 共用此排序；不合并冲突或分叉的快照。
    val rankA = listOf(a.nodes.size, a.protocols, a.fields, -a.errors)
    val rankB = listOf(b.nodes.size, b.protocols, b.fields, -b.errors)
    val difference = rankB.indices.firstOrNull { rankB[it] != rankA[it] } ?: return false
    return rankB[difference] > rankA[difference]
}
