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
import com.ohmyfly.ui.settings.SettingsHint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

@Composable
internal fun FlightDetails(flight: CatalogFlight, onClose: () -> Unit) {
    var config by remember(flight.id) { mutableStateOf<JsonObject?>(null) }
    var history by remember(flight.id) { mutableStateOf<CatalogResultPage?>(null) }
    var failure by remember(flight.id) { mutableStateOf<String?>(null) }
    var cursor by remember(flight.id) { mutableStateOf(0L) }
    var retry by remember(flight.id) { mutableStateOf(0) }
    var reveal by remember(flight.id) { mutableStateOf(false) }
    var connections by remember(flight.id) { mutableStateOf(false) }
    var showHistory by remember(flight.id) { mutableStateOf(false) }
    var loading by remember(flight.id) { mutableStateOf(true) }
    val messages = LocalAppMessages.current
    LaunchedEffect(flight.id, cursor, retry) {
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
            messages.show(AppMessageKind.Failure, "航班详情读取失败")
        } finally { loading = false }
    }
    OverlayBottomSheet(show = true, title = flight.name, onDismissRequest = onClose,
        endAction = { TextButton("关闭", onClick = hapticClick(onClose)) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalWindowInfo.current.containerDpSize.height * 0.72f)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlightSummary(flight, config)
            HorizontalDivider()
            if (loading) CircularProgressIndicator()
            failure?.let {
                SettingsHint(it, isError = true)
                TextButton("重试", onClick = hapticClick { retry++ })
            }
            CatalogAction(if (connections) "收起协议与连接详情" else "协议与连接详情", DriftIcons.Subscriptions,
                Modifier.fillMaxWidth()) { connections = !connections }
            if (connections) config?.let { value ->
                FlightConfiguration(value)
                TextButton(if (reveal) "隐藏敏感配置" else "显示敏感配置（包含凭据）",
                    onClick = hapticClick { reveal = !reveal })
                if (reveal) SelectionContainer { Text(value.toString()) }
            }
            CatalogAction(if (showHistory) "收起结果历史" else "结果历史", com.ohmyfly.ui.settings.SettingsIcons.Timer,
                Modifier.fillMaxWidth()) { showHistory = !showHistory }
            if (showHistory) history?.let { page ->
                if (page.runs.isEmpty()) Text("暂无记录")
                page.runs.forEach { run -> FlightRun(run) }
                if (page.runs.size == 20) TextButton("读取更早记录", enabled = !loading,
                    onClick = hapticClick { cursor = page.runs.last().id })
                if (cursor != 0L) TextButton("返回最新记录", enabled = !loading,
                    onClick = hapticClick { cursor = 0L })
            }
        }
    }
}

@Composable
private fun FlightRun(run: CatalogNodeRun) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${resultStatus(run.status)} · 任务 ${run.taskId}")
        run.finishedAt?.let { Text(kotlin.time.Instant.fromEpochMilliseconds(it).toString()) }
        if (run.reason.isNotBlank()) SettingsHint(run.reason, isError = run.status == "failed")
        run.metrics.forEach { Text("${metricLabel(it.kind)} · ${metricText(it)} · ${resultStatus(it.status)}") }
        run.platforms.forEach { Text("${it.platform} · ${resultStatus(it.status)} ${it.region}") }
    }
}

private fun metricLabel(kind: String) = when (kind) {
    "latency" -> "延迟"
    "download" -> "下载"
    "upload" -> "上传"
    else -> kind
}
