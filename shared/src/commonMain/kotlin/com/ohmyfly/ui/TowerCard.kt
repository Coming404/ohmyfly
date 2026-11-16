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
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
internal fun TowerControl(state: CatalogState, ready: Boolean, onCancel: () -> Unit, onSync: () -> Unit) {
    val status = when {
        state.busy -> "正在同步"
        state.summary.failedTowers > 0 -> "${state.summary.failedTowers} 个塔台异常"
        else -> "全部正常"
    }
    val statusColor = when {
        state.busy -> MiuixTheme.colorScheme.primary
        state.summary.failedTowers > 0 -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.onSurface
    }
    Card(Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(status, modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = statusColor)
            CatalogAction(if (state.busy) "同步中" else "全部同步",
                if (state.busy) DriftIcons.Clear else SettingsIcons.Retry,
                enabled = state.busy || state.ready && ready, onClick = if (state.busy) onCancel else onSync)
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TowerStatistic(state.summary.towers.toString(), "塔台", Modifier.weight(1f))
            TowerStatistic("${state.summary.availableFlights}/${state.summary.flights}", "航班", Modifier.weight(1f))
            TowerStatistic(formatTraffic(state.summary.usedTrafficBytes), "流量", Modifier.weight(1f))
        }
        if (state.busy) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
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
private fun TowerStatistic(value: String, label: String, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

private fun formatTraffic(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1000.0 && unit < units.lastIndex) {
        value /= 1000.0
        unit++
    }
    val rounded = if (value >= 100) value.toLong().toString()
        else ((value * 10).toLong() / 10.0).toString()
    return "$rounded ${units[unit]}"
}

@Composable
internal fun TowerCard(
    tower: CatalogTower, loading: Boolean, ready: Boolean, expanded: Boolean, revision: Long,
    onExpand: () -> Unit, onRefresh: () -> Unit, onRemove: () -> Unit,
    phase: String? = null, onSourceImport: (CatalogSource) -> Unit = {},
    onEdit: () -> Unit = {},
    selected: Boolean = false, selectionMode: Boolean = false, onSelect: () -> Unit = {},
    selectedNodeIds: Set<String> = emptySet(), onNodeSelection: (Set<String>) -> Unit = {},
    nodeSelectionMode: Boolean = false,
) {
    var more by remember(tower.id) { mutableStateOf(false) }
    var importResult by remember(tower.id) { mutableStateOf(false) }
    val messages = LocalAppMessages.current
    // 同步操作按原型固定蓝/红语义，避免动态壁纸主题把正常同步改成其他色相。
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val syncColor = when {
        tower.failure.isNotBlank() -> if (dark) Color(0xFFFF6B6B) else Color(0xFFCF2E2E)
        dark -> Color(0xFF6FA6FF)
        else -> Color(0xFF1F6FE5)
    }
    val select = if (nodeSelectionMode) null else hapticClick(onSelect)
    Card(Modifier.fillMaxWidth().combinedClickable(
        onClick = hapticClick { if (selectionMode) onSelect() else onExpand() },
        onLongClick = select,
    ), cornerRadius = 24.dp) {
        Column(Modifier.fillMaxWidth().background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) CatalogSelectionCheck(selected)
                CatalogTile(if (tower.sourceKind == "http") MirrorIcons.Link else SettingsIcons.File)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tower.name.ifBlank { "塔台 ${tower.id}" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${if (tower.sourceKind == "merged") "${tower.sourceCount} 个来源" else sourceLabel(tower.sourceKind)} · ${phase ?: catalogAge(tower.lastSuccessAt)}",
                        fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
                }
                CatalogBadge(when { loading -> "同步中"; tower.failure.isNotBlank() -> "异常";
                    tower.lastSuccessAt == null -> "未导入"; else -> "正常" }, tower.failure.isNotBlank())
            }
            if (loading) LinearProgressIndicator(Modifier.padding(top = 14.dp).fillMaxWidth())
            TowerUsage(tower)
            if (tower.failure.isNotBlank()) CatalogNotice(tower.failure)
            tower.warnings.forEach { CatalogNotice(it.jsonPrimitive.content, CatalogNoticeKind.Warning) }
            Spacer(Modifier.height(10.dp))
            if (!selectionMode && !nodeSelectionMode) HorizontalDivider()
            if (!selectionMode && !nodeSelectionMode) Row(Modifier.fillMaxWidth()) {
                CatalogAction("航班 ${tower.availableNodeCount}/${tower.nodeCount} ${if (expanded) "⌃" else "⌄"}", DriftIcons.Nodes,
                    Modifier.weight(1.2f), onClick = onExpand)
                CatalogAction(if (tower.sourceKind == "http" || tower.sourceKind == "merged") "同步" else "重导入", SettingsIcons.Retry,
                    Modifier.weight(1f), enabled = ready && !loading, contentColor = syncColor, onClick = onRefresh)
                CatalogAction("更多", MirrorIcons.More, Modifier.weight(1f)) { more = true }
            }
            CatalogExpansion(expanded && !selectionMode) {
                TowerFlights(tower.id, revision, tower.nodeCount, selectedNodeIds, onNodeSelection, nodeSelectionMode)
            }
        }
    }
    if (more) OverlayBottomSheet(show = true, insideMargin = catalogSheetMargin(), title = tower.name,
        onDismissRequest = { more = false }) {
        CatalogAction("编辑塔台", SettingsIcons.Rename, Modifier.fillMaxWidth(), enabled = !loading) {
            more = false; onEdit()
        }
        if (tower.sourceKind == "http" && tower.address.isNotBlank()) CatalogAction("复制订阅地址", SettingsIcons.Copy, Modifier.fillMaxWidth()) {
            copyCatalogText(tower.address)
            more = false
            messages.show(AppMessageKind.Success, "已复制订阅地址")
        }
        CatalogAction("导入结果", SettingsIcons.Hint, Modifier.fillMaxWidth()) {
            more = false; importResult = true
        }
        if (tower.sourceKind == "merged") TowerSources(tower.id, revision) { source -> more = false; onSourceImport(source) }
        tower.lastAttemptAt?.let { Text("最近尝试：${Instant.fromEpochMilliseconds(it)}") }
        CatalogAction("删除塔台", MirrorIcons.Trash, Modifier.fillMaxWidth(), enabled = !loading, destructive = true) {
            more = false; onRemove()
        }
    }
    if (importResult) TowerImportResultSheet(tower) { importResult = false }
}

@Composable
private fun TowerUsage(tower: CatalogTower) {
    tower.usage?.let { usage ->
        val used = if (usage.upload != null && usage.download != null) usage.upload + usage.download else null
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已用 ${formatUsage(used)} / ${formatUsage(usage.total)}", fontSize = 13.sp)
                usage.expireEpochSeconds?.let { expires ->
                    Text(subscriptionExpiryLabel(expires), fontSize = 12.sp,
                        color = if (subscriptionExpired(expires)) MiuixTheme.colorScheme.error
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            // 仅服务端给出总量时绘制比例，同步过程不伪造百分比。
            usage.total?.takeIf { it > 0 && used != null }?.let { total ->
                LinearProgressIndicator(progress = (used!!.toDouble() / total).toFloat().coerceIn(0f, 1f), height = 6.dp)
            }
        }
    }
}

private fun subscriptionExpired(epochSeconds: Long): Boolean =
    epochSeconds <= Clock.System.now().epochSeconds

private fun subscriptionExpiryLabel(epochSeconds: Long): String =
    if (subscriptionExpired(epochSeconds)) "已过期"
    else "到期 ${Instant.fromEpochSeconds(epochSeconds).toString().take(10)}"

private fun sourceLabel(kind: String) = when (kind) { "file" -> "本地文件"; "text" -> "粘贴正文"; else -> "网络订阅" }

internal fun formatUsage(bytes: Long?): String {
    if (bytes == null) return "未知"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) { value /= 1000; unit++ }
    return "${(value * 10).toLong() / 10.0} ${units[unit]}"
}
