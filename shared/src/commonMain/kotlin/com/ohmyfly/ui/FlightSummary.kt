package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("latency" to "延迟", "download" to "下载", "upload" to "上传").forEach { (kind, label) ->
            Row(Modifier.weight(1f).background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(vertical = 12.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(when (kind) { "download" -> SettingsIcons.Download; "upload" -> SettingsIcons.Upload; else -> SettingsIcons.Timer },
                    label, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(compactMetric(metrics[kind], "—"), modifier = Modifier.weight(1f), fontSize = 16.sp,
                    color = metricColor(metrics[kind]), maxLines = 1, autoSize = CatalogTextSizing.Body)
            }
        }
    }
    DetailStatus("所属分组", if (config == null) "正在读取" else
        (config["group"] as? JsonObject)?.getValue("name")?.jsonPrimitive?.content ?: "全部", DriftIcons.Nodes)
    val sources = config?.get("sources") as? JsonArray
    DetailStatus("所属塔台", sources?.map { it.jsonObject.getValue("towerName").jsonPrimitive.content }
        ?.distinct()?.joinToString("、")?.ifBlank { "暂无来源" } ?: "正在读取", DriftIcons.Subscriptions)
    Row(Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
        .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CatalogTile(MirrorIcons.Globe, 32)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("流媒体解锁", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (flight.measurements.platforms.isEmpty()) Text("未测试", fontSize = 14.sp)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                flight.measurements.platforms.forEach { result ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PlatformIcons.forPlatform(result.platform)?.let { PlatformIcon(it, Modifier.size(22.dp)) }
                        Text(resultStatus(result.status), fontSize = 11.sp)
                        if (result.region.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CountryFlag(result.region, Modifier.size(18.dp))
                            Text(result.region, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
    val last = flight.measurements.latestRun
    if (!last?.reason.isNullOrBlank()) CatalogNotice(last!!.reason, if (last.status == "failed") CatalogNoticeKind.Error else CatalogNoticeKind.Warning)
    val measuredAt = last?.finishedAt ?: last?.startedAt
    DetailStatus("最近测试", last?.let { resultStatus(it.status) + (measuredAt?.let { time -> " ${catalogAge(time)}" } ?: "") } ?: "未测试",
        SettingsIcons.Retry)
}

@Composable
private fun DetailStatus(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
        .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CatalogTile(icon, 32)
        Text(label, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value, modifier = Modifier.weight(1f), fontSize = 14.sp, textAlign = TextAlign.End)
    }
}
