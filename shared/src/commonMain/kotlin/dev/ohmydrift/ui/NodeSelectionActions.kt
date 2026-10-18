package dev.ohmydrift.ui

import androidx.compose.runtime.Composable

@Composable
internal fun NodeSelectionActions(
    category: String,
    selectedIds: Set<String>,
    onSelection: (Set<String>) -> Unit,
) {
    val visibleIds = filterNodes(demoNodes, category).map { it.id }.toSet()
    val allSelected = visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)
    HeaderAction(
        label = if (allSelected) "取消当前分类全选" else "全选当前分类",
        icon = DriftIcons.Check,
        enabled = visibleIds.isNotEmpty(),
        onClick = { onSelection(toggleVisibleSelection(selectedIds, visibleIds)) },
    )
    HeaderAction(
        label = "清空所有分类的选择",
        icon = DriftIcons.Clear,
        enabled = selectedIds.isNotEmpty(),
        onClick = { onSelection(emptySet()) },
    )
}
