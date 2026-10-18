package dev.ohmydrift.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DriftTopBar(
    destination: AppDestination,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    dark: Boolean,
    title: String = destination.label,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Row(
            modifier = Modifier.chromeMaterial(backdrop, glassEnabled, dark)
                .padding(horizontal = 12.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) TextButton(text = "返回", onClick = onBack, modifier = Modifier.heightIn(min = 48.dp))
            else Icon(DriftIcons.Wave, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text("ohmydrift", style = MiuixTheme.textStyles.title4, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    title,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            actions()
        }
    }
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
