// 文件正文使用三页复用窗口，滚动边缘自动读取，正文不再逐条套卡片。
package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.logging.copyLogText
import com.ohmyfly.ui.AppMessageKind
import com.ohmyfly.ui.LocalAppMessages
import com.ohmyfly.ui.LocalAppMotionAllowed
import com.ohmyfly.ui.hapticClick
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LogFileContent(state: LogPageState) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogLevelSegments(state.level, "查看级别") {
                scope.launch(start = CoroutineStart.UNDISPATCHED) { state.changeLevel(it) }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            LogCopyButton(state)
        }
        LogTextWindow(state)
    }
}

@Composable
private fun LogTextWindow(state: LogPageState) {
    val list = rememberLazyListState()
    val entries = state.entries
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
    LaunchedEffect(state.revision) { list.scrollToItem(0) }
    LaunchedEffect(state, list, direction) {
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
            contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item("window-start") { if (state.newer != null) SettingsHint("向上滚动读取") }
            items(entries, key = { it.id }) { entry ->
                Column {
                    Text("${logTime(entry.timestampMillis)}  ${entry.level.label}", fontSize = 12.sp,
                        lineHeight = 19.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(entry.message, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
                }
            }
            item("window-end") {
                when {
                    state.busy -> LogBusyHint("正在读取…")
                    state.failure != null -> SettingsHint(state.failure!!, isError = true)
                    entries.isEmpty() -> SettingsHint("此文件没有符合级别的记录")
                    state.older == null -> SettingsHint("已到文件开头")
                    else -> SettingsHint("向下滚动读取")
                }
            }
        }
    }
}

@Composable
private fun LogCopyButton(state: LogPageState) {
    val scope = rememberCoroutineScope()
    val messages = LocalAppMessages.current
    var copying by remember { mutableStateOf(false) }
    LogIconButton(if (copying) "正在复制" else "复制已加载文本", SettingsIcons.Copy,
        enabled = !copying && !state.clearing && state.entries.isNotEmpty(), busy = copying) {
        val text = logText(state.entries)
        copying = true
        scope.launch {
            try {
                withFrameNanos { }
                copyLogText(text)
                messages.show(AppMessageKind.Success, "已复制当前加载的日志文本")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messages.show(AppMessageKind.Failure, "日志复制失败，请重试")
            } finally { copying = false }
        }
    }
}

@Composable
internal fun LogIconButton(
    label: String, icon: ImageVector, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit,
) {
    TooltipBox(text = label) {
        IconButton(onClick = hapticClick(onClick), enabled = enabled, minWidth = 44.dp, minHeight = 44.dp) {
            if (busy && LocalAppMotionAllowed.current) CircularProgressIndicator(
                size = 20.dp, modifier = Modifier.semantics { contentDescription = label },
            )
            else Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
        }
    }
}

@Composable
internal fun LogBusyHint(text: String) {
    Row(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (LocalAppMotionAllowed.current) CircularProgressIndicator(size = 16.dp)
        SettingsHint(text)
    }
}
