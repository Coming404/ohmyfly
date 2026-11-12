package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    if (metrics.values.any { it.valid != null }) Row(Modifier.fillMaxWidth()) {
        listOf("latency" to "延迟", "download" to "下载", "upload" to "上传").forEach { (kind, label) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(metrics[kind]?.valid?.let(::metricText) ?: "—", fontSize = 16.sp)
            }
        }
    }
    val sources = config?.get("sources") as? JsonArray
    DetailStatus("所属塔台", sources?.map { "塔台 #${it.jsonObject["towerId"]!!.jsonPrimitive.content}" }
        ?.distinct()?.joinToString("、")?.ifBlank { "暂无来源" } ?: "正在读取", DriftIcons.Subscriptions)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(MirrorIcons.Globe, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("流媒体解锁", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (flight.measurements.platforms.isEmpty()) Text("未测试", fontSize = 14.sp)
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                flight.measurements.platforms.forEach { result ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PlatformIcons.forPlatform(result.platform)?.let { PlatformIcon(it, Modifier.size(22.dp)) }
                        Text(resultStatus(result.status), fontSize = 11.sp)
                    }
                }
            }
        }
    }
    val last = flight.measurements.latestRun
    val measuredAt = last?.finishedAt ?: last?.startedAt
    DetailStatus("最近测试", last?.let { resultStatus(it.status) + (measuredAt?.let { time -> " · ${catalogAge(time)}" } ?: "") } ?: "未测试",
        SettingsIcons.Retry)
}

@Composable
private fun DetailStatus(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(value, fontSize = 14.sp)
        }
    }
}
