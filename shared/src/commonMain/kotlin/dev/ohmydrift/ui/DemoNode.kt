package dev.ohmydrift.ui

/** Visual fixtures only: no server addresses, credentials or measured results. */
internal data class DemoNode(
    val id: String,
    val name: String,
    val region: String,
    val protocol: String,
    val sampleLatencyMs: Int,
)

internal val demoNodes = listOf(
    DemoNode("tokyo", "东京 · 微风", "亚洲", "Shadowsocks", 38),
    DemoNode("singapore", "新加坡 · 晴空", "亚洲", "Trojan", 56),
    DemoNode("hongkong", "香港 · 海盐", "亚洲", "VLESS", 24),
    DemoNode("frankfurt", "法兰克福 · 远山", "欧洲", "VMess", 168),
)

internal val nodeCategories = listOf("全部", "亚洲", "欧洲")

internal fun filterNodes(nodes: List<DemoNode>, category: String): List<DemoNode> =
    nodes.filter { category == "全部" || it.region == category }

internal fun toggleSelection(selected: Set<String>, id: String): Set<String> =
    if (id in selected) selected - id else selected + id

/** A bulk toggle changes visible items only; hidden selections are preserved. */
internal fun toggleVisibleSelection(selected: Set<String>, visibleIds: Set<String>): Set<String> =
    if (selected.containsAll(visibleIds)) selected - visibleIds else selected + visibleIds
