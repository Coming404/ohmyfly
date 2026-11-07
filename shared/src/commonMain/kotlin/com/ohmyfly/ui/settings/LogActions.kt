// 刷新沿用已确认配置，清理通过统一弹窗确认，反馈复用应用消息。
package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.logging.AppLog
import com.ohmyfly.logging.LogStorageState
import com.ohmyfly.ui.AppMessageKind
import com.ohmyfly.ui.DriftIcons
import com.ohmyfly.ui.LocalAppMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

@Composable
internal fun LogActions(state: LogPageState, storage: LogStorageState) {
    val scope = rememberCoroutineScope()
    val settingsOwner = LocalSettingsEdits.current.session
    val messages = LocalAppMessages.current
    var configuring by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SettingsName("日志文件 ${storage.files} 个 ${logBytes(storage.bytes)}")
                if (configuring) LogBusyHint("正在配置日志存储…")
            }
            LogIconButton(if (state.busy || configuring) "正在刷新" else "刷新日志", SettingsIcons.Retry,
                enabled = !state.busy && !configuring, busy = state.busy || configuring) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    if (!storage.ready || storage.failure != null) {
                        val confirmed = settingsOwner.state.confirmedSettings?.logging
                        if (confirmed == null) {
                            messages.show(AppMessageKind.Failure, "请先重试读取设置")
                            return@launch
                        }
                        configuring = true
                        try { AppLog.configure(confirmed) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            messages.show(AppMessageKind.Failure, "日志配置失败，请重试")
                            return@launch
                        } finally { configuring = false }
                    }
                    if (state.refresh()) messages.show(AppMessageKind.Success, "日志已刷新")
                }
            }
        }
    }
}

@Composable
internal fun LogClearAction(
    state: LogPageState,
    storage: LogStorageState,
    onClearActionChange: ((() -> Unit)?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    val reportClear by rememberUpdatedState(onClearActionChange)
    DisposableEffect(storage.ready, storage.files, state.clearing) {
        reportClear(if (storage.ready && storage.files > 0 && !state.clearing) ({ confirming = true }) else null)
        onDispose { reportClear(null) }
    }
    DisposableEffect(state) { onDispose { state.stop() } }
    MirrorDialog(
        show = confirming, title = "清理全部日志？", icon = MirrorIcons.Trash,
        dismissible = !state.clearing, onDismiss = { confirming = false }, onDismissFinished = {},
        footer = {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsButton("取消", DriftIcons.Clear, Modifier.weight(1f), enabled = !state.clearing) {
                    confirming = false
                }
                SettingsButton(if (state.clearing) "正在清理…" else "确认清理", MirrorIcons.Trash,
                    Modifier.weight(1f), enabled = !state.clearing, destructive = true) {
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { if (state.clear()) confirming = false }
                }
            }
        },
    ) {
        Column(Modifier.padding(20.dp)) {
            SettingsName("将删除全部日志文件，此操作无法撤销。")
            state.failure?.let { SettingsHint(it, isError = true) }
        }
    }
}
