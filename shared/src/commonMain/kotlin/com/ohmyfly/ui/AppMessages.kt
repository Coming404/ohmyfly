package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class AppMessageKind { Default, Success, Failure, Error, Uncertain }

internal class AppMessage(
    val kind: AppMessageKind,
    val text: String,
    val actionLabel: String? = null,
    val onAction: () -> Unit = {},
    val onDismiss: () -> Unit = {},
) {
    val dismissible get() = true
    val undo get() = actionLabel == "撤销"
    val durationMillis get() = if (undo) 4_000L else 3_000L
}

internal class AppMessageState {
    var current by mutableStateOf<AppMessage?>(null)
        private set
    private val pending = ArrayDeque<AppMessage>()

    fun show(kind: AppMessageKind, text: String) {
        val message = AppMessage(kind, text)
        if (current?.undo == true) pending.addLast(message)
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
internal fun AppMessageHost(blocked: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.widthIn(max = 760.dp).fillMaxWidth()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
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
            withDismissAction = message.dismissible,
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
}

@Composable
private fun StaticAppSnackbar(message: AppMessage, messages: AppMessageState) {
    val accessibility = androidx.compose.ui.platform.LocalAccessibilityManager.current
    val data = remember(message) {
        object : SnackbarData {
            override val visuals = SnackbarVisuals(message.text, message.actionLabel, message.dismissible,
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
    val colors = SnackbarDefaults.snackbarColors()
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val (background, foreground) = when (kind) {
        AppMessageKind.Default -> Color(0xFF303030) to Color.White
        AppMessageKind.Success -> if (dark) Color(0xFF183E28) to Color(0xFFB6ECC7)
            else Color(0xFFE4F2E7) to Color(0xFF165B2F)
        AppMessageKind.Failure -> if (dark) Color(0xFF512B2A) to Color(0xFFFFDAD6)
            else Color(0xFFFFE4E1) to Color(0xFFA32A21)
        AppMessageKind.Error -> if (dark) Color(0xFF64242C) to Color(0xFFFFDADE)
            else Color(0xFFFFDADF) to Color(0xFF981B31)
        AppMessageKind.Uncertain -> if (dark) Color(0xFF46371E) to Color(0xFFFFD999)
            else Color(0xFFFFF0D5) to Color(0xFF754D00)
    }
    // 使用组件默认布局，关闭图标不再把单行通知撑成 64 dp。
    Snackbar(data = data,
        modifier = Modifier.semantics {
            liveRegion = if (kind == AppMessageKind.Success || kind == AppMessageKind.Default)
                LiveRegionMode.Polite else LiveRegionMode.Assertive
        },
        colors = SnackbarDefaults.snackbarColors(
            containerColor = background, contentColor = foreground,
            actionContainerColor = if (kind == AppMessageKind.Default) colors.actionContainerColor else foreground.copy(alpha = 0.12f),
            actionContentColor = if (kind == AppMessageKind.Default) colors.actionContentColor else foreground,
            dismissActionContentColor = foreground,
        ), cornerRadius = AppCorners.Notification,
    )
}
