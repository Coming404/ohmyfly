package com.ohmyfly.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import com.ohmyfly.ui.glass.GlassShape
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
    Card(Modifier.fillMaxWidth().clip(GlassShape(24.dp)), cornerRadius = 24.dp, showIndication = true,
        onClick = hapticClick { if (selectionMode) onSelect() else if (flightsEnabled) onExpand() },
        onLongPress = select,
    ) {
        Column(Modifier.fillMaxWidth().catalogSelectionSurface(selected, 24.dp)) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp)) {
                TowerIdentity(tower, loading, selected, selectionMode, syncColor)
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
            }
            if (!selectionMode && !nodeSelectionMode) TowerFooter(tower, loading, ready, flightsEnabled,
                expanded, revision, syncColor, onExpand, onRefresh, onEdit, onRemove, onSourceImport)
            CatalogExpansion(expanded && flightsEnabled && !selectionMode) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
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
                val consumed = used!!.toDouble() / total
                val remaining = 1.0 - consumed
                val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
                // 原型阈值：剩余超过20%为绿，20%以内为橙，耗尽或到期为红。
                val color = when {
                    remaining <= 0 || usage.expireEpochSeconds?.let(::subscriptionExpired) == true ->
                        if (dark) Color(0xFFFF6B6B) else Color(0xFFCF2E2E)
                    remaining <= 0.20 -> if (dark) Color(0xFFF5A524) else Color(0xFFE8900C)
                    else -> if (dark) Color(0xFF3CCB7F) else Color(0xFF237C45)
                }
                LinearProgressIndicator(progress = consumed.toFloat().coerceIn(0f, 1f), height = 6.dp,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = color, backgroundColor = color.copy(alpha = 0.12f)))
            }
        }
    }
}

private fun subscriptionExpired(epochSeconds: Long): Boolean =
    epochSeconds <= Clock.System.now().epochSeconds

private fun subscriptionExpiryLabel(epochSeconds: Long): String =
    if (subscriptionExpired(epochSeconds)) "已过期"
    else "到期 ${Instant.fromEpochSeconds(epochSeconds).toString().take(10)}"

internal fun sourceLabel(kind: String) = when (kind) { "file" -> "本地文件"; "text" -> "粘贴正文"; else -> "网络订阅" }

internal fun formatUsage(bytes: Long?): String = bytes?.let(::controlBytes) ?: "未知"
