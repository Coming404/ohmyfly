// 日志文件共用一张列表卡片，展开单文件有界正文，沿用设置页面外壳与消息。
package com.ohmyfly.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.logging.AppLog
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.ui.LocalAppMessages
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LogsSettingsPage(
    settings: AppSettings,
    onChange: (String, (AppSettings) -> AppSettings) -> Unit,
    padding: PaddingValues,
    onClearActionChange: ((() -> Unit)?) -> Unit,
) {
    val messages = LocalAppMessages.current
    val state = remember { LogPageState(notify = messages::show) }
    val storage by AppLog.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(storage.ready, storage.generation) {
        if (storage.ready && !state.clearing && storage.failure == null) state.refresh(invalidate = true)
    }
    LogClearAction(state, storage, onClearActionChange)
    SettingsList(padding) {
        item("title") { SettingsPageIntro("日志") }
        item("policy") { LogPolicyCard(settings, onChange) }
        item("actions") { LogActions(state, storage) }
        item("status") {
            when {
                state.clearing -> LogBusyHint("正在清理日志…")
                state.busy -> LogBusyHint("正在读取…")
                !storage.ready -> SettingsHint("等待日志存储配置")
                state.files.isEmpty() && state.failure == null -> SettingsName("暂无日志")
            }
            state.failure?.let { SettingsHint(it, isError = true) }
            storage.failure?.let { SettingsHint(it, isError = true) }
            if (storage.dropped > 0) SettingsHint("未保存记录 ${storage.dropped} 条", isError = true)
        }
        if (state.files.isNotEmpty()) item("files") {
            SettingsCard {
                state.files.forEachIndexed { index, file ->
                    key(file.id) {
                        if (index > 0) Box(Modifier.fillMaxWidth().height(0.5.dp)
                            .background(MiuixTheme.colorScheme.dividerLine))
                        val expanded = state.selected?.id == file.id
                        LogFileRow(file, expanded) {
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                state.select(if (expanded) null else file)
                            }
                        }
                        if (expanded) LogFileContent(state)
                    }
                }
            }
        }
    }
}
