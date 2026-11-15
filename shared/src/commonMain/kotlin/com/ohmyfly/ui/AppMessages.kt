package com.ohmyfly.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
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
    val colors = SnackbarDefaults.snackbarColors()
    val scope = rememberCoroutineScope()
    // 仍由SnackbarHost负责动画、超时和动作；自定义内容解除原生两行省略限制。
    Card(modifier = Modifier.fillMaxWidth().padding(SnackbarDefaults.OuterPadding)
        .dropShadow(shape = RoundedCornerShape(SnackbarDefaults.CornerRadius),
            shadow = Shadow(radius = 10.dp, color = Color.Black, alpha = 0.1f))
        .semantics {
            isTraversalGroup = true
            liveRegion = if (kind == AppMessageKind.Success) LiveRegionMode.Polite else LiveRegionMode.Assertive
        }.pointerInput(Unit) { detectTapGestures { /* 消费空白区域点击，避免穿透。 */ } },
        cornerRadius = SnackbarDefaults.CornerRadius,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = colors.containerColor, contentColor = colors.contentColor)) {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).padding(SnackbarDefaults.InsideMargin), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = colors.contentColor)
            // 过长消息在正文区滚动，动作按钮始终可见；普通消息由内容撑高。
            Text(data.visuals.message, modifier = Modifier.weight(1f)
                .heightIn(max = LocalWindowInfo.current.containerDpSize.height * 0.4f)
                .verticalScroll(rememberScrollState()), color = colors.contentColor,
                autoSize = CatalogTextSizing.Notice)
            data.visuals.actionLabel?.takeIf { it.isNotEmpty() }?.let { action ->
                Button(onClick = { scope.launch { data.performAction() } },
                    modifier = Modifier.widthIn(max = 96.dp),
                    minWidth = 26.dp, minHeight = 26.dp, cornerRadius = SnackbarDefaults.ActionCornerRadius,
                    insideMargin = SnackbarDefaults.ActionInsideMargin,
                    colors = ButtonDefaults.buttonColorsPrimary(color = colors.actionContainerColor,
                        contentColor = colors.actionContentColor)) {
                    Text(action, autoSize = CatalogTextSizing.CompactButton,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
