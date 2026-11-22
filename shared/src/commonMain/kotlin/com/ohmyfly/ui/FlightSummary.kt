package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.catalog.CatalogFlight
import com.ohmyfly.ui.settings.MirrorIcons
import com.ohmyfly.ui.settings.SettingsIcons
import kotlinx.serialization.json.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FlightSummary(flight: CatalogFlight, config: JsonObject?) {
    val metrics = flight.measurements.metrics
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            MetricValue(Modifier.weight(1f), SettingsIcons.Timer, compactMetric(metrics["latency"], "—"), metricColor(metrics["latency"]))
            MetricValue(Modifier.weight(1f), SettingsIcons.Download, compactMetric(metrics["download"], "—"), metricColor(metrics["download"]))
            MetricValue(Modifier.weight(1f), SettingsIcons.Upload, compactMetric(metrics["upload"], "—"), metricColor(metrics["upload"]))
        }
        DetailStatus("ContentHash", config?.get("contentHash")?.jsonPrimitive?.contentOrNull
            ?: if (config == null) "正在读取" else "—", DriftIcons.ContentHash, copyable = true)
        val group = (config?.get("group") as? JsonObject)?.get("name")?.jsonPrimitive?.content
            ?: if (config == null) "正在读取" else "全部"
        DetailStatus("所属分组", group, DriftIcons.Nodes)
        val sources = config?.get("sources") as? JsonArray
        DetailStatus("所属塔台", sources?.mapNotNull { (it as? JsonObject)?.get("towerName")?.jsonPrimitive?.content }
            ?.distinct()?.joinToString("、")?.ifBlank { "暂无来源" } ?: if (config == null) "正在读取" else "暂无来源",
            DriftIcons.Subscriptions)
        UnlockSummary(flight)
        val last = flight.measurements.latestRun
        if (!last?.reason.isNullOrBlank()) CatalogNotice(last!!.reason,
            if (last.status == "failed") CatalogNoticeKind.Error else CatalogNoticeKind.Warning)
        val measuredAt = last?.finishedAt ?: last?.startedAt
        DetailStatus("最近测试", last?.let {
            resultStatus(it.status) + (measuredAt?.let { time -> " ${catalogAge(time)}" } ?: "")
        } ?: "未测试", SettingsIcons.Retry)
    }
}

@Composable
private fun MetricValue(modifier: Modifier, icon: ImageVector, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value, fontSize = 13.sp, color = color, maxLines = 1)
    }
}

@Composable
private fun UnlockSummary(flight: CatalogFlight) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CatalogTile(MirrorIcons.Globe, 32)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("流媒体解锁", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (flight.measurements.platforms.isEmpty()) Text("未测试", fontSize = 14.sp)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                flight.measurements.platforms.forEach { result ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlatformIcons.forPlatform(result.platform)?.let { PlatformIcon(it, Modifier.size(18.dp)) }
                        Text(resultStatus(result.status), fontSize = 12.sp)
                        if (result.region.isNotBlank()) {
                            CountryFlag(result.region, Modifier.size(18.dp))
                            Text(result.region, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailStatus(label: String, value: String, icon: ImageVector, copyable: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CatalogTile(icon, 32)
        Text(label, Modifier.width(96.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1)
        val valueText: @Composable () -> Unit = {
            Text(value, Modifier.fillMaxWidth(), fontSize = if (copyable) 11.sp else 14.sp,
                fontFamily = if (copyable) FontFamily.Monospace else FontFamily.Default,
                textAlign = TextAlign.End, softWrap = copyable, maxLines = if (copyable) Int.MAX_VALUE else 2)
        }
        if (copyable) SelectionContainer(Modifier.weight(1f)) { valueText() }
        else Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { valueText() }
    }
}
