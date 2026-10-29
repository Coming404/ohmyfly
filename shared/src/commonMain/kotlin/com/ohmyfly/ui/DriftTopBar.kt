package com.ohmyfly.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
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
import com.ohmyfly.settings.MaterialMode
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LocalHeaderSurface = staticCompositionLocalOf<@Composable () -> Modifier> { { Modifier } }

@Composable
internal fun DriftTopBar(
    title: String,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    dark: Boolean,
    materialMode: MaterialMode,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val pageColor = LocalAppSurfaceColors.current.page
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val surface: @Composable () -> Modifier = {
        Modifier.chromeMaterial(backdrop, glassEnabled, dark, materialMode)
    }
    CompositionLocalProvider(LocalHeaderSurface provides surface) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(
                    0f to pageColor,
                    0.8f to pageColor,
                    1f to pageColor.copy(alpha = 0f),
                ))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null) {
                HeaderAction(
                    label = "返回",
                    icon = MiuixIcons.Basic.ArrowRight,
                    onClick = onBack,
                    iconModifier = Modifier.graphicsLayer { scaleX = if (rtl) 1f else -1f },
                )
            }
            if (title.isNotEmpty()) Text(
                title,
                modifier = Modifier.weight(1f).semantics { heading() },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            ) else Spacer(Modifier.weight(1f))
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
) {
    val tint = MiuixTheme.colorScheme.onSurface
    val surface = LocalHeaderSurface.current
    val interaction = remember { MutableInteractionSource() }
    TooltipBox(text = label) {
        // The 44dp target takes the click; the press overlay stays inside the 38dp glass clip.
        Box(
            Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp).clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = hapticClick(onClick),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(38.dp).then(surface()).indication(interaction, LocalIndication.current),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (enabled) tint else tint.copy(alpha = 0.38f),
                    modifier = Modifier.size(22.dp).then(iconModifier),
                )
            }
        }
    }
}
