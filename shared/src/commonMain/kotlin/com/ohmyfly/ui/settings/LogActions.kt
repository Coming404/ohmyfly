// 日志操作与分页共享 busy 状态；清理后只允许新日志出现。
package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.ohmyfly.logging.AppLog
import com.ohmyfly.logging.LogLevel
import com.ohmyfly.logging.LogStorageState
import com.ohmyfly.ui.DriftIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun LogActions(state: LogPageState, storage: LogStorageState, onPageChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settingsOwner = LocalSettingsEdits.current.session
    var clearing by remember { mutableStateOf(false) }
    var configuring by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val busy = state.busy || configuring
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsCard {
            ChoiceSetting("查看级别", state.level.name, logLevelOptions, SettingsIcons.Filter) {
                if (!busy) scope.launch { onPageChanged(); state.refresh(LogLevel.valueOf(it)) }
            }
        }
        SettingsButton("刷新", SettingsIcons.Retry, enabled = !busy) {
            scope.launch {
                failure = null
                if (!storage.ready || storage.failure != null) {
                    val confirmed = settingsOwner.state.confirmedSettings?.logging
                    if (confirmed == null) { failure = "请先重试读取设置"; return@launch }
                    configuring = true
                    try { AppLog.configure(confirmed) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failure = "日志配置失败，请重试"; return@launch }
                    finally { configuring = false }
                }
                onPageChanged()
                state.refresh()
            }
        }
        if (clearing) {
            SettingsName("清理全部日志？")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsButton("确认清理", SettingsIcons.Reset, enabled = !busy, destructive = true) {
                    scope.launch { if (state.clear()) { onPageChanged(); clearing = false } }
                }
                SettingsButton("取消", DriftIcons.Clear, enabled = !busy) { clearing = false }
            }
        } else SettingsButton("清理全部日志", SettingsIcons.Reset,
            enabled = storage.ready && !busy, destructive = true) { clearing = true }
        failure?.let { SettingsHint(it, isError = true) }
    }
}
