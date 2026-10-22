package dev.ohmydrift.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsActionRow(
    title: String,
    value: String? = null,
    onClick: () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BasicComponent(
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
        onClickLabel = "打开$title",
        role = Role.Button,
        endActions = {
            if (!value.isNullOrBlank()) {
                Text(value, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(width = 10.dp, height = 16.dp).graphicsLayer {
                    scaleX = if (rtl) -1f else 1f
                },
            )
        },
    ) {
        Text(title, fontSize = 16.sp)
    }
}
