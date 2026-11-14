package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

@Composable
internal fun FlightDetails(flight: CatalogFlight, revision: Long) {
    var config by remember(flight.id, revision) { mutableStateOf<JsonObject?>(null) }
    var history by remember(flight.id) { mutableStateOf<CatalogResultPage?>(null) }
    var failure by remember(flight.id) { mutableStateOf<String?>(null) }
    var cursor by remember(flight.id) { mutableStateOf(0L) }
    var retry by remember(flight.id) { mutableStateOf(0) }
    var reveal by remember(flight.id) { mutableStateOf(false) }
    var connections by remember(flight.id) { mutableStateOf(false) }
    var showHistory by remember(flight.id) { mutableStateOf(false) }
    var loading by remember(flight.id) { mutableStateOf(true) }
    var moving by remember(flight.id) { mutableStateOf(false) }
    var sharing by remember(flight.id) { mutableStateOf(false) }
    val messages = LocalAppMessages.current
    LaunchedEffect(flight.id, revision, flight.measurements, cursor, retry) {
        loading = true
        failure = null
        try {
            if (config == null) config = withContext(Dispatchers.Default) {
                catalogCall("detail") { put("id", flight.id) }
            }
            history = readCatalogResults(nodeId = flight.id, before = cursor, limit = 20)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            failure = "航班详情读取失败"
        } finally { loading = false }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlightSummary(flight, config)
            CatalogAction("移动到分组", DriftIcons.Nodes) { moving = true }
            HorizontalDivider()
            if (loading) CircularProgressIndicator()
            failure?.let {
                CatalogNotice(it)
                CatalogTextButton("重试", onClick = hapticClick { retry++ })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CatalogAction("测速", DriftIcons.Speed, Modifier.weight(1f)) {
                    messages.show(AppMessageKind.Uncertain, "测速入口已保留，执行器尚未接入")
                }
                CatalogAction(if (flight.enabled) "停用" else "启用", com.ohmyfly.ui.settings.MirrorIcons.Power, Modifier.weight(1f)) {
                    appCatalog.changeNodes(setOf(flight.id), if (flight.enabled) "disable" else "enable") { ok, text ->
                        messages.show(if (ok) AppMessageKind.Success else AppMessageKind.Failure, text)
                    }
                }
                CatalogAction("分享", com.ohmyfly.ui.settings.SettingsIcons.Backup, Modifier.weight(1f)) { sharing = true }
            }
            CatalogAction(if (connections) "收起协议与连接详情" else "协议与连接详情", DriftIcons.Subscriptions,
                Modifier.fillMaxWidth()) { connections = !connections }
            if (connections) config?.let { value ->
                FlightConfiguration(value)
                CatalogTextButton(if (reveal) "隐藏敏感配置" else "显示敏感配置（包含凭据）",
                    onClick = hapticClick { reveal = !reveal })
                if (reveal) SelectionContainer { Text(value.toString(), autoSize = CatalogTextSizing.Body) }
            }
            CatalogAction(if (showHistory) "收起结果历史" else "结果历史", com.ohmyfly.ui.settings.SettingsIcons.Timer,
                Modifier.fillMaxWidth()) { showHistory = !showHistory }
            if (showHistory) history?.let { page ->
                if (page.runs.isEmpty()) Text("暂无记录")
                page.runs.forEach { run -> FlightRun(run) }
                if (page.runs.size == 20) CatalogTextButton("读取更早记录", enabled = !loading,
                    onClick = hapticClick { cursor = page.runs.last().id })
                if (cursor != 0L) CatalogTextButton("返回最新记录", enabled = !loading,
                    onClick = hapticClick { cursor = 0L })
            }
    }
    if (sharing) CatalogShareSheet(setOf(flight.id), onClose = { sharing = false })
    if (moving) FlightGroupMove(setOf(flight.id), onMoved = { moving = false; retry++ }, onClose = { moving = false })
}

@Composable
private fun FlightRun(run: CatalogNodeRun) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${resultStatus(run.status)} · 任务 ${run.taskId}")
        run.finishedAt?.let { Text(kotlin.time.Instant.fromEpochMilliseconds(it).toString()) }
        if (run.reason.isNotBlank()) CatalogNotice(run.reason,
            if (run.status == "failed") CatalogNoticeKind.Error else CatalogNoticeKind.Warning)
        run.metrics.forEach { Text("${metricLabel(it.kind)} · ${metricText(it)} · ${resultStatus(it.status)}") }
        run.platforms.forEach {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlatformIcons.forPlatform(it.platform)?.let { icon -> PlatformIcon(icon, Modifier.size(20.dp)) }
                CountryFlag(it.region, Modifier.size(20.dp))
                Text("${it.platform} · ${resultStatus(it.status)} ${it.region}")
            }
        }
    }
}

private fun metricLabel(kind: String) = when (kind) {
    "latency" -> "延迟"
    "download" -> "下载"
    "upload" -> "上传"
    else -> kind
}
