// 文件正文使用三页复用窗口，滚动边缘自动读取，正文不再逐条套卡片。
package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.logging.LogEntry
import com.ohmyfly.logging.LogLevel
import com.ohmyfly.logging.copyLogText
import com.ohmyfly.ui.AppMessageKind
import com.ohmyfly.ui.LocalAppMessages
import com.ohmyfly.ui.LocalAppMotionAllowed
import kotlinx.coroutines.flow.collect
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class LogContentSnapshot(
    val entries: List<LogEntry>, val busy: Boolean, val failure: String?,
    val hasOlder: Boolean, val hasNewer: Boolean,
)

private fun LogPageState.contentSnapshot() =
    LogContentSnapshot(entries, busy, failure, older != null, newer != null)

@Composable
internal fun LogFileContent(state: LogPageState, fileId: String) {
    val active = state.selected?.id == fileId
    // 退出动画保留本文件最后一帧，切换文件时不显示新文件正文，也不继续触发读取。
    var content by remember(fileId) { mutableStateOf(state.contentSnapshot()) }
    if (active) content = state.contentSnapshot()
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        LogTextWindow(state, content, active)
    }
}

@Composable
private fun LogTextWindow(state: LogPageState, content: LogContentSnapshot, active: Boolean) {
    val list = rememberLazyListState()
    val entries = content.entries
    val direction = remember(state.revision) { LogReadDirection() }
    val scroll = remember(direction) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) direction.userScroll(available.y)
                return Offset.Zero
            }
        }
    }
    val viewport = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val maxHeight = minOf(viewport * 0.62f, 560.dp).coerceAtLeast(96.dp)
    var restoring by remember { mutableStateOf(false) }
    LaunchedEffect(state.revision, active) { if (active) list.scrollToItem(0) }
    LaunchedEffect(state, list, direction, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow {
            val visible = list.layoutInfo.visibleItemsInfo
            if (state.busy || restoring || state.failure != null || visible.isEmpty()) null
            else direction.request(atTop = visible.first().index == 0,
                atBottom = visible.last().index >= state.entries.size,
                older = state.older, newer = state.newer)
        }.collect { request ->
            if (request != null) {
                val anchor = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index > 0 && it.index <= state.entries.size }
                val revision = state.revision
                restoring = true
                try {
                    state.more(request.forward)
                    val index = state.entries.indexOfFirst { it.id == anchor?.key }
                    if (revision == state.revision && index >= 0) {
                        withFrameNanos { }
                        list.scrollToItem(index + 1, -(anchor?.offset ?: 0))
                    }
                } finally { restoring = false }
            }
        }
    }
    SelectionContainer {
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = maxHeight)
            .nestedScroll(scroll),
            contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(com.ohmyfly.ui.ChromeMetrics.LogRecordGap)) {
            item("window-start") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (content.hasNewer) SettingsHint("向上滚动读取")
                    }
                    LogCopyButton(state, entries, active)
                }
            }
            items(entries, key = { it.id }) { entry ->
                Column {
                    Text("${logTime(entry.timestampMillis)}  详情", fontSize = com.ohmyfly.ui.ChromeMetrics.LogBodySize,
                        lineHeight = com.ohmyfly.ui.ChromeMetrics.LogLineHeight, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(entry.message, fontSize = com.ohmyfly.ui.ChromeMetrics.LogBodySize,
                        lineHeight = com.ohmyfly.ui.ChromeMetrics.LogLineHeight, fontWeight = FontWeight.Normal)
                }
            }
            item("window-end") {
                when {
                    content.busy -> LogBusyHint("正在读取…")
                    content.failure != null -> SettingsHint(content.failure, isError = true)
                    entries.isEmpty() -> SettingsHint("此文件没有符合级别的记录")
                    !content.hasOlder -> SettingsHint("已到文件开头")
                    else -> SettingsHint("向下滚动读取")
                }
            }
        }
    }
}

@Composable
private fun LogCopyButton(state: LogPageState, entries: List<LogEntry>, active: Boolean) {
    val messages = LocalAppMessages.current
    top.yukonga.miuix.kmp.basic.IconButton(
        enabled = active && !state.clearing && entries.isNotEmpty(),
        modifier = Modifier.size(com.ohmyfly.ui.ChromeMetrics.ControlSize),
        backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
        onClick = {
        // 点击时立即写入系统剪贴板；切到其他应用不必等待下一帧或页面协程。
        try {
            val text = logText(entries)
            copyLogText(text)
            messages.show(AppMessageKind.Success, "已复制当前加载的日志文本")
        } catch (_: Exception) {
            messages.show(AppMessageKind.Failure, "日志复制失败，请重试")
        }
    }) {
        top.yukonga.miuix.kmp.basic.Icon(SettingsIcons.Copy, "复制已加载文本",
            Modifier.size(com.ohmyfly.ui.ChromeMetrics.LogCopyIconSize),
            tint = com.ohmyfly.ui.appIconTint())
    }
}

@Composable
internal fun LogIconButton(
    label: String, icon: ImageVector, enabled: Boolean = true, busy: Boolean = false,
    background: androidx.compose.ui.graphics.Color = com.ohmyfly.ui.LocalAppSurfaceColors.current.page,
    onClick: () -> Unit,
) {
    com.ohmyfly.ui.InlineChromeIconButton(label, icon, onClick, enabled = enabled, busy = busy,
        background = background, controlSize = 34.dp, iconSize = 16.dp)
}

@Composable
internal fun LogBusyHint(text: String) {
    Row(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (LocalAppMotionAllowed.current) CircularProgressIndicator(size = 16.dp)
        SettingsHint(text)
    }
}
