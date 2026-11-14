package com.ohmyfly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.*
import com.ohmyfly.ui.settings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.time.Instant

@Composable
internal fun TowerControl(state: CatalogState, ready: Boolean, onCancel: () -> Unit, onSync: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TowerStatistic(state.summary.towers, "塔台", Modifier.weight(1f))
            Box(Modifier.width(0.5.dp).height(30.dp).background(MiuixTheme.colorScheme.outline.copy(alpha = 0.2f)))
            TowerStatistic(state.summary.flights, "航班", Modifier.weight(1f))
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.busy) "已完成 ${state.taskCompleted}/${state.taskTotal} · 等待批次 ${state.queuedBatches}" else "下拉刷新目录", modifier = Modifier.weight(1f),
                fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            CatalogAction(if (state.busy) "取消" else "全部同步", if (state.busy) DriftIcons.Clear else SettingsIcons.Retry,
                enabled = state.busy || state.ready && ready, onClick = if (state.busy) onCancel else onSync)
        }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                state.sourceTasks.values.take(4).forEach { task ->
                    Text("${task.name} · ${task.phase}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.sourceTasks.size > 4) Text("另有 ${state.sourceTasks.size - 4} 个来源处理中", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TowerStatistic(count: Long, label: String, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(count.toString(), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
internal fun TowerCard(
    tower: CatalogTower, loading: Boolean, ready: Boolean, expanded: Boolean, revision: Long,
    onExpand: () -> Unit, onRefresh: () -> Unit, onRemove: () -> Unit,
    phase: String? = null, onSourceImport: (CatalogSource) -> Unit = {},
    selected: Boolean = false, selectionMode: Boolean = false, onSelect: () -> Unit = {},
    selectedNodeIds: Set<String> = emptySet(), onNodeSelection: (Set<String>) -> Unit = {},
    nodeSelectionMode: Boolean = false,
) {
    var more by remember(tower.id) { mutableStateOf(false) }
    // 同步操作按原型固定蓝/红语义，避免动态壁纸主题把正常同步改成其他色相。
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val syncColor = when {
        tower.failure.isNotBlank() -> if (dark) Color(0xFFFF6B6B) else Color(0xFFCF2E2E)
        dark -> Color(0xFF6FA6FF)
        else -> Color(0xFF1F6FE5)
    }
    val select = hapticClick(onSelect)
    Card(Modifier.fillMaxWidth().combinedClickable(
        onClick = hapticClick { if (selectionMode) onSelect() else onExpand() },
        onLongClick = select,
    )) {
        Column(Modifier.fillMaxWidth().background(if (selected) MiuixTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) CatalogSelectionCheck(selected)
                CatalogTile(if (tower.sourceKind == "http") MirrorIcons.Link else SettingsIcons.File)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tower.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${if (tower.sourceKind == "merged") "${tower.sourceCount} 个来源" else sourceLabel(tower.sourceKind)} · ${phase ?: catalogAge(tower.lastSuccessAt)}",
                        fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
                }
                CatalogBadge(when { loading -> "同步中"; tower.failure.isNotBlank() -> "异常";
                    tower.lastSuccessAt == null -> "未导入"; else -> "已导入" }, tower.failure.isNotBlank())
            }
            if (loading) LinearProgressIndicator(Modifier.padding(top = 14.dp).fillMaxWidth())
            TowerUsage(tower)
            if (tower.failure.isNotBlank()) CatalogNotice(tower.failure)
            tower.warnings.forEach { CatalogNotice(it.jsonPrimitive.content, CatalogNoticeKind.Warning) }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            if (!selectionMode && !nodeSelectionMode) Row(Modifier.fillMaxWidth()) {
                CatalogAction("航班 ${tower.nodeCount} ${if (expanded) "⌃" else "⌄"}", DriftIcons.Nodes,
                    Modifier.weight(1.2f), onClick = onExpand)
                CatalogAction(if (tower.sourceKind == "http" || tower.sourceKind == "merged") "同步" else "重导入", SettingsIcons.Retry,
                    Modifier.weight(1f), enabled = ready && !loading, contentColor = syncColor, onClick = onRefresh)
                CatalogAction("更多", MirrorIcons.More, Modifier.weight(1f)) { more = true }
            }
            CatalogExpansion(expanded && !selectionMode) {
                TowerFlights(tower.id, revision, selectedNodeIds, onNodeSelection, nodeSelectionMode)
            }
        }
    }
    if (more) OverlayBottomSheet(show = true, title = tower.name, onDismissRequest = { more = false }) {
        if (tower.sourceKind == "merged") TowerSources(tower.id, revision) { source -> more = false; onSourceImport(source) }
        tower.lastAttemptAt?.let { Text("最近尝试：${Instant.fromEpochMilliseconds(it)}") }
        CatalogAction("删除塔台", MirrorIcons.Trash, Modifier.fillMaxWidth(), enabled = !loading, destructive = true) {
            more = false; onRemove()
        }
    }
}

@Composable
private fun TowerUsage(tower: CatalogTower) {
    tower.usage?.let { usage ->
        val used = if (usage.upload != null && usage.download != null) usage.upload + usage.download else null
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已用 ${formatUsage(used)} / ${formatUsage(usage.total)}", fontSize = 13.sp)
                usage.expireEpochSeconds?.let { Text("到期 ${Instant.fromEpochSeconds(it).toString().take(10)}", fontSize = 12.sp) }
            }
            // 仅服务端给出总量时绘制比例，同步过程不伪造百分比。
            usage.total?.takeIf { it > 0 && used != null }?.let { total ->
                LinearProgressIndicator(progress = (used!!.toDouble() / total).toFloat().coerceIn(0f, 1f), height = 6.dp)
            }
        }
    }
}

private fun sourceLabel(kind: String) = when (kind) { "file" -> "本地文件"; "text" -> "粘贴正文"; else -> "网络订阅" }

internal fun formatUsage(bytes: Long?): String {
    if (bytes == null) return "未知"
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return "${(value * 10).toLong() / 10.0} ${units[unit]}"
}
