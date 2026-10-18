package dev.ohmydrift.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun NodesScreen(
    listState: LazyListState,
    category: String,
    selectedIds: Set<String>,
    onCategory: (String) -> Unit,
    onSelection: (Set<String>) -> Unit,
) {
    val visible = filterNodes(demoNodes, category)
    val visibleIds = visible.map { it.id }.toSet()
    val allVisibleSelected = visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SectionHeading("挑选你的下一站", "卡片列表 / 分类 / 多选") }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                nodeCategories.forEach { item ->
                    ChoiceOption(
                        label = item,
                        selected = category == item,
                        onClick = { onCategory(item) },
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("已选择 ${selectedIds.size} / ${demoNodes.size} 个", style = MiuixTheme.textStyles.title2)
                    Text("当前分类 ${visible.size} 个；切换分类会保留其他地区的选择。")
                    ActionButton(
                        label = if (allVisibleSelected) "取消本类全选" else "全选当前分类",
                        icon = DriftIcons.Check,
                        enabled = visibleIds.isNotEmpty(),
                        onClick = { onSelection(toggleVisibleSelection(selectedIds, visibleIds)) },
                    )
                    ActionButton(
                        label = "清空所有选择",
                        icon = DriftIcons.Clear,
                        enabled = selectedIds.isNotEmpty(),
                        onClick = { onSelection(emptySet()) },
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            item { Text("该分类暂无演示节点。") }
        }
        items(visible, key = { it.id }) { node ->
            val checked = node.id in selectedIds
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { onSelection(toggleSelection(selectedIds, node.id)) },
                        )
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(node.name, style = MiuixTheme.textStyles.title4)
                        Text(
                            "${node.region} · ${node.protocol}\n示例延迟 ${node.sampleLatencyMs} ms · 非实测",
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
        item {
            ActionButton(
                label = "真实测速尚未接入",
                icon = DriftIcons.Play,
                enabled = false,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { DemoFootnote() }
    }
}
