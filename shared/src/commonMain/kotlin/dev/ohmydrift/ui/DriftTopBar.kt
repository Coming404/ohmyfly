package dev.ohmydrift.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DriftTopBar(
    destination: AppDestination,
    color: Color,
    actions: @Composable RowScope.() -> Unit,
) {
    SmallTopAppBar(
        title = "ohmydrift",
        subtitle = destination.label,
        color = color,
        navigationIcon = {
            Icon(
                DriftIcons.Wave,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        },
        actions = actions,
    )
}

@Composable
internal fun HeaderAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
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
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
