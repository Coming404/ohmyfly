package dev.ohmydrift.ui

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
                    BasicComponent(title = "订阅来源", endActions = { Text(subscriptionCount.toString()) })
                    BasicComponent(title = "节点总数", endActions = { Text(nodes.size.toString()) })
                    BasicComponent(title = "已选择", endActions = { Text(selectedCount.toString()) })
                }
            }
        }
        item {
            Column {
                SmallTitle("最近测速")
                Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    BasicComponent(title = "暂无测速记录")
                }
            }
        }
        item {
            Column {
                SmallTitle("快捷入口")
                Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    BasicComponent(
                        title = "订阅",
                        startAction = { OverviewIcon(DriftIcons.Subscriptions) },
                        onClick = onSubscriptions,
                        role = Role.Button,
                    )
                    BasicComponent(
                        title = "测速",
                        startAction = { OverviewIcon(DriftIcons.Speed) },
                        onClick = onSpeed,
                        role = Role.Button,
                    )
                    BasicComponent(
                        title = "节点库",
                        startAction = { OverviewIcon(DriftIcons.Nodes) },
                        onClick = onNodes,
                        role = Role.Button,
                    )
                    BasicComponent(
                        title = "设置",
                        startAction = { OverviewIcon(DriftIcons.Settings) },
                        onClick = onSettings,
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
