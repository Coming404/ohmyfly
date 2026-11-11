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
        label = if (allSelected) "取消本页全选" else "全选本页",
        icon = DriftIcons.Check,
        enabled = visibleIds.isNotEmpty(),
        onClick = { onSelection(toggleVisibleSelection(selectedIds, visibleIds)) },
    )
    HeaderAction(
        label = "清空本页选择",
        icon = DriftIcons.Clear,
        enabled = selectedIds.isNotEmpty(),
        onClick = { onSelection(emptySet()) },
    )
}
