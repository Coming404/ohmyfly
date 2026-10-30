package com.ohmyfly.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ohmyfly.settings.MaterialMode
import com.ohmyfly.ui.glass.GlassShape
import com.ohmyfly.ui.settings.MirrorIcons
import com.ohmyfly.ui.settings.SettingsIcons
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class AppMessageKind { Success, Failure, Error, Uncertain }

internal class AppMessage(val kind: AppMessageKind, val text: String) {
    val dismissible get() = kind != AppMessageKind.Success
    val durationMillis get() = if (dismissible) 8_000L else 3_000L
}

internal class AppMessageState {
    var current by mutableStateOf<AppMessage?>(null)
        private set

    fun show(kind: AppMessageKind, text: String) {
        current = AppMessage(kind, text)
    }

    fun dismiss(message: AppMessage? = current) {
        if (current === message) current = null
    }
}

internal val LocalAppMessages = staticCompositionLocalOf<AppMessageState> {
    error("AppMessageState must be provided by PersistedSettings")
}

@Composable
internal fun AppMessageHost(
    padding: PaddingValues,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    dark: Boolean,
    materialMode: MaterialMode,
    blocked: Boolean,
) {
    val messages = LocalAppMessages.current
    val motionAllowed = LocalAppMotionAllowed.current
    val accessibility = LocalAccessibilityManager.current
    val direction = LocalLayoutDirection.current
    val target = messages.current.takeUnless { blocked }
    var displayed by remember { mutableStateOf<AppMessage?>(null) }
    val position = remember { Animatable(-1f) }
    val movement = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    LaunchedEffect(blocked, messages.current) { if (blocked) messages.dismiss() }
    LaunchedEffect(target, motionAllowed, blocked) {
        if (!motionAllowed || blocked) {
            displayed = target
            position.snapTo(0f)
        } else {
            if (displayed != null) position.animateTo(-1f, movement)
            displayed = target
            if (target != null) {
                position.snapTo(-1f)
                position.animateTo(0f, movement)
            }
        }
    }
    val visible = if (!motionAllowed || blocked) target else displayed
    LaunchedEffect(visible, accessibility) {
        val message = visible ?: return@LaunchedEffect
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = message.durationMillis,
            containsIcons = true,
            containsText = true,
            containsControls = message.dismissible,
        ) ?: message.durationMillis
        delay(timeout)
        messages.dismiss(message)
    }
    if (visible != null) {
        // 裁剪从实测顶栏底部开始，滑动过程也不能覆盖顶栏按钮。
        Box(
            Modifier.fillMaxSize().padding(
                start = padding.calculateStartPadding(direction) + 16.dp,
                end = padding.calculateEndPadding(direction) + 16.dp,
                top = padding.calculateTopPadding(),
            ).clipToBounds(),
            contentAlignment = Alignment.TopCenter,
        ) {
            key(visible) {
                AppMessageSurface(visible, backdrop, glassEnabled, dark, materialMode,
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            val offset = if (motionAllowed) (position.value * placeable.height).roundToInt() else 0
                            placeable.placeRelative(0, offset)
                        }
                    },
                    onDismiss = { messages.dismiss(visible) },
                )
            }
        }
    }
}

@Composable
private fun AppMessageSurface(
    message: AppMessage,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    dark: Boolean,
    materialMode: MaterialMode,
    modifier: Modifier,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val (icon, tint, label) = when (message.kind) {
        AppMessageKind.Success -> Triple(DriftIcons.Check, colors.primary, "成功")
        AppMessageKind.Failure -> Triple(DriftIcons.Clear, colors.error, "失败")
        AppMessageKind.Error -> Triple(MirrorIcons.Alert, colors.onErrorContainer, "错误")
        AppMessageKind.Uncertain -> Triple(SettingsIcons.Timer, colors.onSurfaceSecondary, "待确认")
    }
    Row(
        modifier.padding(top = 8.dp)
            .chromeMaterial(backdrop, glassEnabled, dark, materialMode, remember { GlassShape(20.dp) })
            // 消息盖在列表上方，卡片内的点击不能落到下面的设置行。
            .pointerInput(Unit) {}
            .semantics {
                isTraversalGroup = true
                liveRegion = if (message.kind == AppMessageKind.Success) LiveRegionMode.Polite else LiveRegionMode.Assertive
            }.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = tint)
        Text(message.text, modifier = Modifier.weight(1f), color = colors.onSurface, style = MiuixTheme.textStyles.body2)
        if (message.dismissible) {
            IconButton(onClick = hapticClick(onDismiss), minWidth = 44.dp, minHeight = 44.dp) {
                Icon(DriftIcons.Clear, contentDescription = "关闭消息", modifier = Modifier.size(20.dp), tint = colors.onSurface)
            }
        }
    }
}
