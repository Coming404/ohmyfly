package dev.ohmydrift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ohmydrift.settings.MaterialMode
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(
                0f to pageColor,
                0.8f to pageColor,
                1f to pageColor.copy(alpha = 0f),
            ))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            Box(Modifier.chromeMaterial(backdrop, glassEnabled, dark, materialMode)) {
                HeaderAction(
                    label = "返回",
                    icon = MiuixIcons.Basic.ArrowRight,
                    onClick = onBack,
                    iconModifier = Modifier.graphicsLayer { scaleX = if (rtl) 1f else -1f },
                )
            }
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
            Row(
                modifier = Modifier.chromeMaterial(backdrop, glassEnabled, dark, materialMode),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
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
    TooltipBox(text = label) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = if (enabled) Modifier else Modifier.semantics { disabled() },
            minHeight = 48.dp,
            minWidth = 48.dp,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) tint else tint.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp).then(iconModifier),
            )
        }
    }
}
