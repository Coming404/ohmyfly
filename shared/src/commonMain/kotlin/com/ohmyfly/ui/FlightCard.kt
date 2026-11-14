package com.ohmyfly.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.graphics.Color
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
internal fun FlightCard(flight: CatalogFlight, selected: Boolean, selectionMode: Boolean,
    onSelect: () -> Unit, onDetails: () -> Unit, expanded: Boolean, revision: Long) {
    Card(Modifier.fillMaxWidth()) {
        FlightRow(flight, selected, selectionMode, onSelect, onDetails, expanded)
        CatalogExpansion(expanded && !selectionMode) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider()
                FlightDetails(flight, revision)
            }
        }
    }
}

@Composable
internal fun FlightRow(flight: CatalogFlight, selected: Boolean = false, selectionMode: Boolean = false,
    onSelect: (() -> Unit)? = null, onDetails: () -> Unit, expanded: Boolean = false) {
    val select = onSelect?.let { hapticClick(it) }
    Row(Modifier.fillMaxWidth()
        .background(if (selected) MiuixTheme.colorScheme.primaryContainer else Color.Transparent)
        .alpha(if (flight.enabled) 1f else 0.5f).combinedClickable(
        onClick = hapticClick { if (selectionMode) onSelect?.invoke() else onDetails() }, onLongClick = select,
        role = Role.Button, onLongClickLabel = if (selected) "取消选择" else "选择航班")
        .semantics { if (onSelect != null) customActions = listOf(CustomAccessibilityAction(
            if (selected) "取消选择" else "选择航班") { select?.invoke(); true }) }
        .padding(horizontal = 12.dp, vertical = 10.dp).heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selectionMode) CatalogSelectionCheck(selected)
        if (flight.regionCode.isNotBlank()) {
            Box(Modifier.size(36.dp).background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center) { CountryFlag(flight.regionCode, Modifier.size(24.dp)) }
        } else CatalogTile(com.ohmyfly.ui.settings.MirrorIcons.Globe, 36)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(flight.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(flight.type.uppercase(), fontSize = 12.sp, color = MiuixTheme.colorScheme.primary, maxLines = 1)
                Text(flight.region, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
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
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = metricColor(latency))
            Text(compactMetric(download, "—"),
                fontSize = 12.sp, color = metricColor(download))
        }
        if (!selectionMode) Icon(MiuixIcons.Basic.ArrowRight, if (expanded) "收起详情" else "展开详情",
            Modifier.size(16.dp).rotate(if (expanded) 90f else 0f), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
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
" -> "已中断"
    "unlocked" -> "已解锁"
    "locked" -> "未解锁"
    else -> value
}
