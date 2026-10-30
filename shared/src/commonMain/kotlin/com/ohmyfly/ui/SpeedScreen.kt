package com.ohmyfly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
internal fun SpeedScreen(
    listState: LazyListState,
    nodes: List<NodeSummary>,
    selectedIds: Set<String>,
    onNodes: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val selected = nodes.filter { it.id in selectedIds }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding.withPageSpacing(LocalLayoutDirection.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                BasicComponent(
                    title = "测试范围",
                    summary = if (selected.isEmpty()) "未选择航班" else "已选择 ${selected.size} 个航班",
                    onClick = hapticClick(onNodes),
                    role = Role.Button,
                )
            }
        }
        item { SmallTitle("待起飞航班") }
        if (selected.isEmpty()) {
            item { BasicComponent(title = "暂无待起飞航班") }
        }
        items(selected, key = { it.id }) { node ->
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                BasicComponent(title = node.name, summary = "${node.region} · ${node.protocol}")
            }
        }
        item {
            TextButton(
                text = "开始起飞",
                enabled = false,
                onClick = {},
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
            )
        }
    }
}
