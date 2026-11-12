package com.ohmyfly.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ohmyfly.ui.settings.MirrorIcons
import com.ohmyfly.ui.settings.SettingsIcons
import top.yukonga.miuix.kmp.basic.*

internal enum class AppMessageKind { Success, Failure, Error, Uncertain }

internal class AppMessage(
    val kind: AppMessageKind,
    val text: String,
    val actionLabel: String = "关闭",
    val onAction: () -> Unit = {},
    val onDismiss: () -> Unit = {},
) {
    val dismissible get() = kind != AppMessageKind.Success
    val durationMillis get() = if (dismissible) 8_000L else 3_000L
}

internal class AppMessageState {
    var current by mutableStateOf<AppMessage?>(null)
        private set
    private val pending = ArrayDeque<AppMessage>()

    fun show(kind: AppMessageKind, text: String) {
        val message = AppMessage(kind, text)
        if (current?.actionLabel == "撤销") pending.addLast(message)
        else current = message
    }

    fun show(message: AppMessage) {
        if (current == null) current = message else pending.addLast(message)
    }

    fun dismiss(message: AppMessage? = current) {
        if (current !== message || message == null) return
        current = pending.removeFirstOrNull()
        message.onDismiss()
    }

    fun perform(message: AppMessage) {
        if (current !== message) return
        current = pending.removeFirstOrNull()
        message.onAction()
    }
}

internal val LocalAppMessages = staticCompositionLocalOf<AppMessageState> {
    error("AppMessageState must be provided by PersistedSettings")
}

@Composable
internal fun AppMessageHost(blocked: Boolean) {
    val messages = LocalAppMessages.current
    val message = messages.current
    val motion = LocalAppMotionAllowed.current
    // 0.9.4 的 showSnackbar 取消不清除宿主条目；模式切换必须丢弃旧宿主，防止重复动作。
    val host = remember(message, blocked, motion) { SnackbarHostState() }
    LaunchedEffect(message, blocked, motion) {
        if (message == null || blocked || !motion) return@LaunchedEffect
        val result = host.showSnackbar(
            message = message.text,
            actionLabel = message.actionLabel,
            duration = SnackbarDuration.Custom(message.durationMillis),
        )
        if (result == SnackbarResult.ActionPerformed) messages.perform(message)
        else messages.dismiss(message)
    }
    if (!blocked && !motion && message != null) {
        StaticAppSnackbar(message, messages)
    } else if (!blocked) {
        SnackbarHost(state = host) { data ->
            message?.let { AppSnackbar(data, it.kind) }
        }
    }
}

@Composable
private fun StaticAppSnackbar(message: AppMessage, messages: AppMessageState) {
    val accessibility = androidx.compose.ui.platform.LocalAccessibilityManager.current
    val data = remember(message) {
        object : SnackbarData {
            override val visuals = SnackbarVisuals(message.text, message.actionLabel, false,
                SnackbarDuration.Custom(message.durationMillis))
            override suspend fun dismiss() { messages.dismiss(message) }
            override suspend fun performAction() { messages.perform(message) }
        }
    }
    LaunchedEffect(message, accessibility) {
        val duration = accessibility?.calculateRecommendedTimeoutMillis(
            message.durationMillis, containsIcons = true, containsText = true, containsControls = true,
        ) ?: message.durationMillis
        kotlinx.coroutines.delay(duration)
        messages.dismiss(message)
    }
    AppSnackbar(data, message.kind)
}

@Composable
private fun AppSnackbar(data: SnackbarData, kind: AppMessageKind) {
    val (icon, label) = when (kind) {
        AppMessageKind.Success -> DriftIcons.Check to "成功"
        AppMessageKind.Failure -> DriftIcons.Clear to "失败"
        AppMessageKind.Error -> MirrorIcons.Alert to "错误"
        AppMessageKind.Uncertain -> SettingsIcons.Timer to "待确认"
    }
    // 0.9.4 没有 leadingIcon 插槽；保留原生按钮、手势、超时与背景，只给图标留位。
    Box(Modifier.fillMaxWidth()) {
        Snackbar(data, insideMargin = PaddingValues(start = 52.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            modifier = Modifier.semantics {
                liveRegion = if (kind == AppMessageKind.Success) LiveRegionMode.Polite else LiveRegionMode.Assertive
            })
        Icon(icon, contentDescription = label,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp).size(20.dp),
            tint = SnackbarDefaults.snackbarColors().contentColor)
    }
}
