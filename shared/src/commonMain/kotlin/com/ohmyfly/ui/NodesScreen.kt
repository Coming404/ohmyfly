package com.ohmyfly.ui

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
    selectedIds: Set<String>,
    onSelection: (Set<String>) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    catalog: com.ohmyfly.catalog.CatalogState,
    onPage: (Int) -> Unit,
) {
    val visible = nodes
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding.withPageSpacing(LocalLayoutDirection.current, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            if (!catalog.ready || catalog.matchCount == null) {
                BasicComponent(title = if (catalog.failure == null) "正在筛选…" else "筛选未完成")
                catalog.failure?.let { message ->
                    com.ohmyfly.ui.settings.SettingsHint(message, isError = true)
                    com.ohmyfly.ui.settings.SettingsButton("重试", com.ohmyfly.ui.settings.SettingsIcons.Retry) {
                        com.ohmyfly.catalog.appCatalog.start()
                    }
                }
            } else {
                com.ohmyfly.catalog.CatalogPager(catalog.flightPage, catalog.matchCount, catalog.failure,
                    onPrevious = { onPage(catalog.flightPage - 1) }, onNext = { onPage(catalog.flightPage + 1) },
                    onRetry = { com.ohmyfly.catalog.appCatalog.start() }, loading = catalog.flightLoading)
            }
        }
        if (catalog.ready && !catalog.flightLoading && catalog.failure == null && catalog.matchCount == 0L) {
            item {
                Card(Modifier.fillMaxWidth()) { BasicComponent(title = "暂无匹配航班") }
            }
        } else if (catalog.ready && !catalog.flightLoading && catalog.matchCount != null && nodes.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    BasicComponent(title = "本页已选择 ${selectedIds.size} / ${nodes.size} 个")
                }
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
                            listOfNotNull(node.region.takeIf { it.isNotBlank() }, node.protocol,
                                node.latencyMs?.let { "$it ms" } ?: "未起飞").joinToString(" · "),
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
