package com.ohmyfly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun OverviewScreen(
    listState: LazyListState,
    nodes: List<NodeSummary>,
    subscriptionCount: Int,
    selectedCount: Int,
    onNodes: () -> Unit,
    onSettings: () -> Unit,
    onSubscriptions: () -> Unit,
    onSpeed: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding.withPageSpacing(LocalLayoutDirection.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                SmallTitle("概况")
                Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    BasicComponent(title = "塔台来源", endActions = { Text(subscriptionCount.toString()) })
                    BasicComponent(title = "航班总数", endActions = { Text(nodes.size.toString()) })
                    BasicComponent(title = "已选择", endActions = { Text(selectedCount.toString()) })
                }
            }
        }
        item {
            Column {
                SmallTitle("最近起飞")
                Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    BasicComponent(title = "暂无起飞记录")
                }
            }
        }
        item {
            Column {
                SmallTitle("快捷入口")
                Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    BasicComponent(
                        title = "塔台",
                        startAction = { OverviewIcon(DriftIcons.Subscriptions) },
                        onClick = hapticClick(onSubscriptions),
                        role = Role.Button,
                    )
                    BasicComponent(
                        title = "起飞",
                        startAction = { OverviewIcon(DriftIcons.Speed) },
                        onClick = hapticClick(onSpeed),
                        role = Role.Button,
                    )
                    BasicComponent(
                        title = "航班",
                        startAction = { OverviewIcon(DriftIcons.Nodes) },
                        onClick = hapticClick(onNodes),
                        role = Role.Button,
                    )
                    BasicComponent(
                        title = "设置",
                        startAction = { OverviewIcon(DriftIcons.Settings) },
                        onClick = hapticClick(onSettings),
                        role = Role.Button,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
}
