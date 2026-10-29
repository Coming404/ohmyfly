package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.MediaSettings
import com.ohmyfly.settings.SettingsWriteResult
import com.ohmyfly.settings.SpeedSettings
import com.ohmyfly.settings.SubscriptionSettings
import com.ohmyfly.ui.DriftIcons
import kotlinx.coroutines.launch

internal enum class SettingsResetTarget(val page: SettingsPage?, val title: String, val description: String) {
    Speed(SettingsPage.Speed, "恢复本页默认", "恢复全部测速参数；不更改其他设置，不删除塔台、航班或历史。"),
    Media(SettingsPage.Media, "恢复本页默认", "恢复全部流媒体与 AI 检测偏好；不更改其他设置，不删除测试结果。"),
    Subscription(SettingsPage.Subscription, "恢复本页默认", "恢复塔台请求与过滤偏好；不更改其他设置，不删除已导入的业务目录。"),
    All(null, "恢复全部设置", "重置所有设置，包含外观、测试偏好、默认镜像列表与默认选择；自定义镜像将被移除。不会删除塔台、航班目录或历史。"),
    ;

    fun apply(current: AppSettings): AppSettings = when (this) {
        Speed -> current.copy(speed = SpeedSettings())
        Media -> current.copy(media = MediaSettings())
        Subscription -> current.copy(subscription = SubscriptionSettings())
        All -> AppSettings()
    }
}

internal class SettingsResetState {
    var confirming by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var success by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun ask() { confirming = true; success = false; error = null }
    fun cancel() { if (!busy) { confirming = false; error = null } }

    suspend fun submit(reset: suspend () -> SettingsWriteResult) {
        if (busy || !confirming) return
        busy = true
        try {
            when (reset()) {
                is SettingsWriteResult.Confirmed -> { success = true; confirming = false; error = null }
                SettingsWriteResult.Failed -> error = "恢复失败，设置未更改，请重试。"
                SettingsWriteResult.Uncertain -> error = "恢复结果待确认，请重试读取后核对设置。"
                SettingsWriteResult.Rejected -> error = "暂时无法恢复，请检查设置读取状态后重试。"
            }
        } finally { busy = false }
    }
}

@Composable
internal fun ResetSettings(target: SettingsResetTarget) {
    val state = remember(target) { SettingsResetState() }
    val edits = LocalSettingsEdits.current
    val scope = rememberCoroutineScope()
    SettingsCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsPageIntro(if (state.confirming) "确认${target.title}？" else target.title, SettingsIcons.Reset)
            SettingsHint(target.description)
            if (state.success) SettingsHint("已恢复默认设置")
            state.error?.let { SettingsHint(it, isError = true) }
            if (state.confirming) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (edits.session.state.readFailed) {
                        SettingsButton("重试读取", SettingsIcons.Retry) { edits.session.retryRead() }
                    } else {
                        SettingsButton(if (state.busy) "恢复中…" else "确认恢复", SettingsIcons.Reset,
                            enabled = !state.busy, destructive = true) {
                            scope.launch { state.submit { edits.reset(target) } }
                        }
                    }
                    SettingsButton("取消", DriftIcons.Clear, enabled = !state.busy) { state.cancel() }
                }
            } else {
                SettingsButton(target.title, SettingsIcons.Reset, enabled = !edits.session.state.readFailed) { state.ask() }
            }
        }
    }
}
