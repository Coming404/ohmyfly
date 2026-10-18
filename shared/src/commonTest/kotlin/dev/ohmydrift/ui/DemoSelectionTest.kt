package dev.ohmydrift.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoSelectionTest {
    @Test
    fun categoriesFilterWithoutChangingSource() {
        assertEquals(4, filterNodes(demoNodes, "全部").size)
        assertEquals(3, filterNodes(demoNodes, "亚洲").size)
        assertEquals(listOf("frankfurt"), filterNodes(demoNodes, "欧洲").map { it.id })
        assertTrue(filterNodes(demoNodes, "未知分类").isEmpty())
        assertEquals(4, demoNodes.size)
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
        val asianIds = filterNodes(demoNodes, "亚洲").map { it.id }.toSet()
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
    fun fixturesHaveStableUniqueIdsAndKnownCategories() {
        assertEquals(demoNodes.size, demoNodes.map { it.id }.toSet().size)
        assertTrue(demoNodes.all { it.region in nodeCategories && it.sampleLatencyMs > 0 })
    }
}
