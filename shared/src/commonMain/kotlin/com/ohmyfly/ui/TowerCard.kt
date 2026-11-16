package com.ohmyfly.ui

import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    val syncing = state.sourceTasks.values.filter { it.phase != "排队" }.map { it.parentId }.distinct().size
    val status = when {
        state.busy -> if (syncing > 0) "$syncing 个塔台同步中" else "等待同步"
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
    // 同步操作按原型固定蓝/红语义，避免动态壁纸主题把正常同步改成其他色相。
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val syncColor = when {
        tower.failure.isNotBlank() -> if (dark) Color(0xFFFF6B6B) else Color(0xFFCF2E2E)
        dark -> Color(0xFF6FA6FF)
        else -> Color(0xFF1F6FE5)
    }
    val select = if (nodeSelectionMode) null else hapticClick(onSelect)
    val flightsEnabled = !loading && tower.failure.isBlank() && tower.lastSuccessAt != null
    // 交互放入 Card 的连续圆角表面内，长按遮罩和卡片共用相同裁剪。
    Card(Modifier.fillMaxWidth(), cornerRadius = 24.dp, showIndication = true,
        onClick = hapticClick { if (selectionMode) onSelect() else if (flightsEnabled) onExpand() },
        onLongPress = select,
    ) {
        Column(Modifier.fillMaxWidth().background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top) {
                if (selectionMode) CatalogSelectionCheck(selected)
                CatalogTile(if (tower.sourceKind == "http") MirrorIcons.Link else SettingsIcons.File,
                    color = when {
                        loading -> if (dark) Color(0xFFFFD180) else Color(0xFF996000)
                        tower.failure.isNotBlank() -> syncColor
                        else -> MiuixTheme.colorScheme.primary
                    })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tower.name.ifBlank { "塔台 ${tower.id}" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (loading) "正在同步" else "${if (tower.sourceKind == "merged") "${tower.sourceCount} 个来源" else sourceLabel(tower.sourceKind)} · ${catalogAge(tower.lastSuccessAt)}",
                        fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
                }
                TowerStatusBadge(tower, loading)
            }
            if (loading) Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(phase ?: "正在同步", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            if (!loading) TowerUsage(tower)
            if (!loading && tower.failure.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                CatalogNotice(tower.failure)
            }
            tower.warnings.forEach {
                Spacer(Modifier.height(8.dp))
                CatalogNotice(it.jsonPrimitive.content, CatalogNoticeKind.Warning)
            }
            Spacer(Modifier.height(10.dp))
            if (!selectionMode && !nodeSelectionMode) HorizontalDivider()
            if (!selectionMode && !nodeSelectionMode) Row(Modifier.fillMaxWidth()) {
                CatalogAction("航班 ${tower.availableNodeCount}/${tower.nodeCount}", DriftIcons.Nodes,
                    Modifier.weight(1.2f), enabled = flightsEnabled, expanded = expanded, onClick = onExpand)
                CatalogAction(if (tower.sourceKind == "http" || tower.sourceKind == "merged") "同步" else "重导入", SettingsIcons.Retry,
                    Modifier.weight(1f), enabled = ready && !loading, contentColor = syncColor, onClick = onRefresh)
                TowerMoreMenu(tower, loading, revision, Modifier.weight(1f), onEdit, onRemove, onSourceImport)
            }
            CatalogExpansion(expanded && flightsEnabled && !selectionMode) {
                Column(Modifier.padding(top = 12.dp)) {
                    TowerFlights(tower.id, revision, tower.nodeCount, selectedNodeIds, onNodeSelection, nodeSelectionMode)
                }
            }
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
