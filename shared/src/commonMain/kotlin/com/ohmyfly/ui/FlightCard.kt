package com.ohmyfly.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.CatalogFlight
import com.ohmyfly.catalog.CatalogMetric
import com.ohmyfly.catalog.CatalogMetricSummary
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToLong

@Composable
internal fun FlightCard(flight: CatalogFlight, selected: Boolean, onSelect: () -> Unit, onDetails: () -> Unit) {
    Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
        FlightRow(flight, selected, onSelect, onDetails)
    }
}

@Composable
internal fun FlightRow(flight: CatalogFlight, selected: Boolean = false,
    onSelect: (() -> Unit)? = null, onDetails: () -> Unit) {
    val select = onSelect?.let { hapticClick(it) }
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = hapticClick(onDetails), onLongClick = select,
        role = Role.Button, onLongClickLabel = if (selected) "取消选择" else "选择航班")
        .semantics { if (onSelect != null) customActions = listOf(CustomAccessibilityAction(
            if (selected) "取消选择" else "选择航班") { select?.invoke(); true }) }
        .padding(horizontal = 12.dp, vertical = 10.dp).heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CatalogTile(if (selected) DriftIcons.Check else com.ohmyfly.ui.settings.MirrorIcons.Globe, 36)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(flight.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(flight.type.uppercase(), fontSize = 12.sp, color = MiuixTheme.colorScheme.primary, maxLines = 1)
                // 目录没有地区字段，不从名称猜国旗或制造地区数据。
                val unlocked = flight.measurements.platforms.filter { it.status == "unlocked" }
                unlocked.take(2).forEach { result ->
                    PlatformIcons.forPlatform(result.platform)?.let { PlatformIcon(it, Modifier.size(17.dp)) }
                }
                if (unlocked.size > 2) Text("+${unlocked.size - 2}", fontSize = 11.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val latency = flight.measurements.metrics["latency"]
            val download = flight.measurements.metrics["download"]
            Text(compactMetric(latency, "未测试"),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface)
            Text(compactMetric(download, "—"),
                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Icon(MiuixIcons.Basic.ArrowRight, null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

internal fun compactMetric(summary: CatalogMetricSummary?, missing: String): String {
    if (summary == null) return missing
    val shown = summary.valid ?: summary.latest ?: return missing
    val previous = summary.valid != null && summary.latest?.status != "succeeded"
    return metricText(shown) + if (previous) " · 上次成功" else ""
}

internal fun metricText(metric: CatalogMetric): String {
    val value = metric.value ?: return resultStatus(metric.status)
    return if (metric.kind == "latency") "${value.roundToLong()} ms"
    else "${(value / 1024 / 1024 * 10).roundToLong() / 10.0} MB/s"
}

internal fun resultStatus(value: String): String = when (value) {
    "queued" -> "等待中"
    "running" -> "进行中"
    "succeeded" -> "成功"
    "failed" -> "失败"
    "cancelled" -> "已取消"
    "interrupted" -> "已中断"
    "unlocked" -> "已解锁"
    "locked" -> "未解锁"
    else -> value
}
