package com.ohmyfly.ui

import androidx.compose.runtime.Composable

@Composable
internal fun NodeSelectionActions(
    nodes: List<NodeSummary>,
    selectedIds: Set<String>,
    onSelection: (Set<String>) -> Unit,
) {
    val visibleIds = nodes.map { it.id }.toSet()
    val allSelected = visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)
    HeaderAction(
        label = if (allSelected) "取消已加载全选" else "全选已加载",
        icon = DriftIcons.Check,
        enabled = visibleIds.isNotEmpty(),
        onClick = { onSelection(toggleVisibleSelection(selectedIds, visibleIds)) },
    )
    HeaderAction(
        label = "清空已加载选择",
        icon = DriftIcons.Clear,
        enabled = selectedIds.isNotEmpty(),
        onClick = { onSelection(emptySet()) },
    )
}
