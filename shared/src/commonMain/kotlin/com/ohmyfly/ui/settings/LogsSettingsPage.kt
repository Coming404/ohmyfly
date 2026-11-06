// 日志页按需读取一页正文，详情展开后可复制；文件管理不在组合线程执行。
package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.logging.AppLog
import com.ohmyfly.logging.LogEntry
import com.ohmyfly.logging.copyLogText
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.ui.DriftIcons
import kotlinx.coroutines.launch
import kotlin.time.Instant

@Composable
internal fun LogsSettingsPage(
    settings: AppSettings,
    onChange: (String, (AppSettings) -> AppSettings) -> Unit,
    padding: PaddingValues,
) {
    val state = remember { LogPageState() }
    val storage by AppLog.state.collectAsState()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<LogEntry?>(null) }
    LaunchedEffect(storage.ready) { if (storage.ready) state.refresh() }
    SettingsList(padding) {
        item("title") { SettingsPageIntro("日志") }
        item("policy") { LogPolicyCard(settings, onChange) }
        item("storage") {
            SettingsCard {
                SettingsInfo("文件占用", "", SettingsIcons.Backup,
                    "${storage.bytes / 1024} KiB · ${storage.files} 个文件")
                if (storage.dropped > 0) SettingsInfo("未保存记录", "", SettingsIcons.Hint, storage.dropped.toString())
                storage.failure?.let { SettingsInfo("存储失败", it, SettingsIcons.Hint, isError = true) }
            }
        }
        item("actions") { LogActions(state, storage) { selected = null } }
        item("status") {
            when {
                state.failure != null -> SettingsHint(state.failure!!, isError = true)
                state.busy -> SettingsName("读取中…")
                !storage.ready -> SettingsName("等待日志存储配置")
                state.entries.isEmpty() -> SettingsName(if (state.next == null) "暂无日志" else "本页没有符合级别的记录")
                else -> SettingsName("${state.entries.size} 条记录")
            }
        }
        items(state.entries, key = { it.id }) { entry ->
            LogRecord(entry, selected?.id == entry.id) { selected = if (selected?.id == entry.id) null else entry }
        }
        item("paging") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsButton("较新", SettingsIcons.Timer, enabled = state.canGoBack && !state.busy) {
                    scope.launch { selected = null; state.newer() }
                }
                SettingsButton("较早", SettingsIcons.Timer, enabled = state.next != null && !state.busy) {
                    scope.launch { selected = null; state.older() }
                }
            }
        }
    }
}

@Composable
private fun LogRecord(entry: LogEntry, expanded: Boolean, onClick: () -> Unit) {
    SettingsCard {
        val time = Instant.fromEpochMilliseconds(entry.timestampMillis).toString()
        SettingsExpandableRow(entry.message.take(100), "${time.substringAfter('T')} · ${entry.level.label}",
            SettingsIcons.Hint, expanded, onClick)
        SettingsExpansion(expanded) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val text = "${Instant.fromEpochMilliseconds(entry.timestampMillis)} · ${entry.level.label}\n${entry.message}"
                SelectionContainer { SettingsName(text) }
                SettingsButton("复制", SettingsIcons.Copy) { copyLogText(text) }
            }
        }
    }
}
