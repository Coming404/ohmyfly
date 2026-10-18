package dev.ohmydrift.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeSelectionTest {
    private val nodes = listOf(
        NodeSummary("tokyo", "Tokyo", "亚洲", "Shadowsocks"),
        NodeSummary("singapore", "Singapore", "亚洲", "Trojan"),
        NodeSummary("hongkong", "Hong Kong", "亚洲", "VLESS"),
        NodeSummary("frankfurt", "Frankfurt", "欧洲", "VMess"),
    )

    @Test
    fun categoriesFilterWithoutChangingSource() {
        assertEquals(4, filterNodes(nodes, "全部").size)
        assertEquals(3, filterNodes(nodes, "亚洲").size)
        assertEquals(listOf("frankfurt"), filterNodes(nodes, "欧洲").map { it.id })
        assertTrue(filterNodes(nodes, "未知分类").isEmpty())
        assertEquals(4, nodes.size)
    }

    @Test
    fun singleToggleAddsThenRemovesWithoutMutation() {
        val original = setOf("tokyo")
        val added = toggleSelection(original, "singapore")
        assertEquals(setOf("tokyo", "singapore"), added)
        assertEquals(original, toggleSelection(added, "singapore"))
        assertEquals(setOf("tokyo"), original)
    }

    @Test
    fun selectAllIsScopedToCurrentCategory() {
        val asianIds = filterNodes(nodes, "亚洲").map { it.id }.toSet()
        val selected = toggleVisibleSelection(setOf("frankfurt", "tokyo"), asianIds)
        assertEquals(asianIds + "frankfurt", selected)
        assertEquals(setOf("frankfurt"), toggleVisibleSelection(selected, asianIds))
    }

    @Test
    fun emptyVisibleSetDoesNotRemoveHiddenSelections() {
        val selected = setOf("tokyo", "frankfurt")
        assertEquals(selected, toggleVisibleSelection(selected, emptySet()))
    }

    @Test
    fun nodeMetadataDoesNotInventLatencyResults() {
        assertEquals(nodes.size, nodes.map { it.id }.toSet().size)
        assertTrue(nodes.all { it.region in nodeCategories(nodes) && it.latencyMs == null })
    }

    @Test
    fun emptyCatalogueContainsNoNodesAndOnlyTheAllCategory() {
        assertEquals(listOf("全部"), nodeCategories(emptyList()))
        assertTrue(filterNodes(emptyList(), "全部").isEmpty())
        assertTrue(toggleVisibleSelection(emptySet(), emptySet()).isEmpty())
    }
}
