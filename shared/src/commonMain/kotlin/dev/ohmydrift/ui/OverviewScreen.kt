package dev.ohmydrift.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
    expanded: Boolean,
    reduceMotion: Boolean,
    onExpand: () -> Unit,
    onNodes: () -> Unit,
    onAppearance: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val direction = LocalLayoutDirection.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(direction),
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = contentPadding.calculateEndPadding(direction),
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()
                    .animateContentSize(tween(if (reduceMotion) 0 else 240)),
            ) {
                BasicComponent(
                    title = "让连接，轻盈一点。",
                    summary = "ohmydrift · 界面预览 · 非实测数据",
                    startAction = { OverviewIcon(DriftIcons.Wave) },
                )
                BasicComponent(
                    title = if (expanded) "收起展示" else "展开动效",
                    summary = "仅展开说明，不启动真实测速",
                    onClick = onExpand,
                    role = Role.Button,
                )
                if (expanded) {
                    BasicComponent(summary = "共享 Miuix 界面，使用固定演示数据，不会发送网络请求或消耗测试流量。")
                }
            }
        }
        item {
            Column {
                SmallTitle("演示概况")
                Card(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    BasicComponent(
                        title = "演示节点",
                        endActions = {
                            Text(demoNodes.size.toString(), color = MiuixTheme.colorScheme.primary)
                        },
                    )
                    BasicComponent(
                        title = "地区分类",
                        endActions = {
                            Text(demoNodes.map { it.region }.distinct().size.toString(), color = MiuixTheme.colorScheme.primary)
                        },
                    )
                }
            }
        }
        item {
            Column {
                SmallTitle("从这里开始")
                Card(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    BasicComponent(
                        title = "查看节点",
                        summary = "分类筛选与节点多选",
                        startAction = { OverviewIcon(DriftIcons.Nodes) },
                        onClick = onNodes,
                        role = Role.Button,
                    )
                    BasicComponent(
                        title = "调整外观",
                        summary = "明暗模式与减少动态效果",
                        startAction = { OverviewIcon(DriftIcons.Theme) },
                        onClick = onAppearance,
                        role = Role.Button,
                    )
                }
            }
        }
        item {
            Column {
                SmallTitle("本次预览")
                Card(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    BasicComponent(
                        title = "Miuix 默认组件排版",
                        summary = "默认文字层级、行内距和卡片圆角",
                        startAction = { OverviewIcon(DriftIcons.Overview) },
                    )
                    BasicComponent(
                        title = "Expressive · Spec2025",
                        summary = "MonetSystem · 蓝色种子生成 · 跟随系统明暗",
                        startAction = { OverviewIcon(DriftIcons.Theme) },
                    )
                }
            }
        }
        item { BasicComponent(summary = "演示界面，尚未接入订阅与真实测速。") }
    }
}

@Composable
private fun OverviewIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
}
