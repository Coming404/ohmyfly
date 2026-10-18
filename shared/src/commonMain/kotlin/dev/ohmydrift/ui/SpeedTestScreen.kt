package dev.ohmydrift.ui

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun SpeedTestScreen(
    listState: LazyListState,
    selectedIds: Set<String>,
    onNodes: () -> Unit,
) {
    val selected = demoNodes.filter { it.id in selectedIds }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                BasicComponent(
                    title = "真实测速尚未接入",
                    summary = "当前只预览所选演示节点，不会发送测试请求或消耗测试流量。",
                )
                BasicComponent(
                    title = "选择测试节点",
                    summary = "已选择 ${selected.size} 个演示节点，前往节点库调整。",
                    onClick = onNodes,
                    role = Role.Button,
                )
            }
        }
        item { SmallTitle("待测节点 · 演示") }
        if (selected.isEmpty()) {
            item { BasicComponent(summary = "尚未选择节点，请先到节点库选择。") }
        }
        items(selected, key = { it.id }) { node ->
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                BasicComponent(title = node.name, summary = "${node.region} · ${node.protocol} · 非实测")
            }
        }
        item {
            ActionButton(
                label = "开始测速（尚未接入）",
                icon = DriftIcons.Play,
                enabled = false,
                onClick = {},
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
            )
        }
    }
}
