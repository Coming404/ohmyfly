package dev.ohmydrift.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
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
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.07f))
                        .animateContentSize(tween(if (reduceMotion) 0 else 240))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        DriftIcons.Wave,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                    Text("让连接，轻盈一点。", style = MiuixTheme.textStyles.title1)
                    Text("这是 ohmydrift 的第一块画布。\n先感受组件，再慢慢接入真实能力。")
                    Text("UI PREVIEW / 01", color = MiuixTheme.colorScheme.primary)
                    ActionButton(
                        label = if (expanded) "收起展示" else "展开动效",
                        icon = if (expanded) DriftIcons.Clear else DriftIcons.Play,
                        onClick = onExpand,
                        primary = true,
                    )
                    if (expanded) {
                        Text(
                            "轻轻展开，不急不躁。\n这里展示尺寸过渡，不会启动测速或消耗测试流量。",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    "演示节点", demoNodes.size.toString().padStart(2, '0'),
                    Modifier.weight(1f).fillMaxHeight(),
                )
                StatCard(
                    "地区分类", demoNodes.map { it.region }.distinct().size.toString().padStart(2, '0'),
                    Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        item {
            SectionHeading("从这里开始", "按钮有图标，点击也有去处。")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("查看节点", DriftIcons.Nodes, onNodes, Modifier.fillMaxWidth())
                ActionButton("调整外观", DriftIcons.Theme, onAppearance, Modifier.fillMaxWidth())
            }
        }
        item { SectionHeading("本次预览", "卡片、列表与文字的基本节奏。") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    FeatureRow(DriftIcons.Overview, "清晰的层次", "品牌卡、数据卡与成组卡片列表。")
                    FeatureRow(DriftIcons.Check, "直接的反馈", "分类筛选、节点多选与已选数量。")
                    FeatureRow(DriftIcons.Theme, "自己的节奏", "浅色、深色，以及更少的动态效果。")
                }
            }
        }
        item { DemoFootnote() }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = MiuixTheme.textStyles.title1, color = MiuixTheme.colorScheme.primary)
            Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MiuixTheme.textStyles.title4)
            Text(description, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
