package com.ohmyfly.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun NodesScreen(
    listState: LazyListState,
    nodes: List<NodeSummary>,
    category: String,
    selectedIds: Set<String>,
    onCategory: (String) -> Unit,
    onSelection: (Set<String>) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val visible = filterNodes(nodes, category)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding.withPageSpacing(LocalLayoutDirection.current, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (nodes.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) { BasicComponent(title = "暂无航班") }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    nodeCategories(nodes).forEach { item ->
                        ChoiceOption(item, category == item, onClick = { onCategory(item) })
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = "已选择 ${selectedIds.size} / ${nodes.size} 个",
                        summary = "当前分类 ${visible.size} 个",
                    )
                }
            }
            if (visible.isEmpty()) {
                item { BasicComponent(title = "当前分类暂无航班") }
            }
        }
        items(visible, key = { it.id }) { node ->
            val checked = node.id in selectedIds
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = hapticChange { onSelection(toggleSelection(selectedIds, node.id)) },
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(node.name, style = MiuixTheme.textStyles.title4)
                        Text(
                            "${node.region} · ${node.protocol} · ${node.latencyMs?.let { "$it ms" } ?: "未起飞"}",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Checkbox(
                        state = if (checked) ToggleableState.On else ToggleableState.Off,
                        onClick = null,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}
