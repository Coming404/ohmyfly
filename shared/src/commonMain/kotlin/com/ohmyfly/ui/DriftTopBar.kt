// 顶栏控件沿用系统安全区，滚动内容不再被整条状态栏遮罩覆盖。
package com.ohmyfly.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronForward
import top.yukonga.miuix.kmp.glass.glassPress
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LocalHeaderSurface = staticCompositionLocalOf<@Composable () -> Modifier> { { Modifier } }

@Composable
internal fun DriftTopBar(
    title: String,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    dark: Boolean,
    titleCount: Long? = null,
    titleCapsule: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val safeArea = Modifier.windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    )
    // 没有固定内容时只提供安全区，不留下空的顶栏主体。
    if (title.isEmpty() && onBack == null && actions == null) {
        Spacer(Modifier.fillMaxWidth().then(safeArea))
        return
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val surface: @Composable () -> Modifier = {
        Modifier.chromeMaterial(backdrop, glassEnabled, dark)
    }
    CompositionLocalProvider(LocalHeaderSurface provides surface) {
        Row(
            // 安全区只用于控件定位，顶部左右两侧不绘制额外遮挡。
            modifier = Modifier.fillMaxWidth().then(safeArea)
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null) {
                HeaderAction(
                    label = "返回",
                    icon = MiuixIcons.Glass.ChevronForward,
                    onClick = onBack,
                    iconModifier = Modifier.graphicsLayer { scaleX = if (rtl) 1f else -1f },
                )
            }
            if (title.isNotEmpty()) Box(Modifier.weight(1f)) {
                if (titleCapsule || titleCount != null) Row(Modifier.then(surface()).heightIn(min = ChromeMetrics.ControlSize)
                    .padding(horizontal = ChromeMetrics.TitleInset, vertical = 8.dp).semantics { heading() },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, Modifier.weight(1f, fill = false), fontSize = ChromeMetrics.TitleSize,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    titleCount?.let { Text(it.toString(), fontSize = ChromeMetrics.TitleSize,
                        fontWeight = FontWeight.Bold, maxLines = 1) }
                } else Text(title, modifier = Modifier.semantics { heading() }, fontSize = ChromeMetrics.TitleSize,
                    fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else Spacer(Modifier.weight(1f))
            if (actions != null) {
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}

@Composable
internal fun HeaderAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier,
    tint: Color = MiuixTheme.colorScheme.onSurface,
) {
    val surface = LocalHeaderSurface.current
    val interaction = remember { MutableInteractionSource() }
    TooltipBox(text = label) {
        // 可见玻璃面与点击区一致，独立按钮不再套用更小的内圆。
        Box(
            Modifier.defaultMinSize(minWidth = ChromeMetrics.ControlSize, minHeight = ChromeMetrics.ControlSize)
                .glassPress(interaction, enabled && LocalAppMotionAllowed.current)
                .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = hapticClick(onClick),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(ChromeMetrics.ControlSize).then(surface()).indication(interaction, LocalIndication.current),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (enabled) tint else tint.copy(alpha = 0.38f),
                    modifier = Modifier.size(ChromeMetrics.IconSize).then(iconModifier),
                )
            }
        }
    }
}

@Composable
internal fun HeaderTextAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MiuixTheme.colorScheme.primary,
) {
    val surface = LocalHeaderSurface.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.defaultMinSize(minWidth = ChromeMetrics.ControlSize, minHeight = ChromeMetrics.ControlSize)
            .glassPress(interaction, enabled && LocalAppMotionAllowed.current)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled,
                role = Role.Button, onClick = hapticClick(onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.heightIn(min = ChromeMetrics.ControlSize).then(surface()).indication(interaction, LocalIndication.current)
            .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, label, Modifier.size(ChromeMetrics.IconSize), tint = if (enabled) tint else tint.copy(alpha = 0.38f))
            Text(label, color = if (enabled) tint else tint.copy(alpha = 0.38f), fontSize = ChromeMetrics.ActionTextSize,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
