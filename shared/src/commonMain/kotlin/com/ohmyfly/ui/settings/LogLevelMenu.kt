// 日志查看级别使用紧凑菜单；弹出、选中与关闭反馈交给既有 Miuix 菜单。
package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.logging.LogLevel
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.graphicsLayer

@Composable
internal fun LogLevelMenu(value: LogLevel, enabled: Boolean, onChange: (LogLevel) -> Unit) {
    OverlayIconDropdownMenu(
        entry = DropdownEntry(LogLevel.entries.map { level ->
            DropdownItem(text = level.label, selected = level == value,
                onClick = { if (level != value) onChange(level) })
        }),
        enabled = enabled, minWidth = 64.dp, minHeight = 44.dp,
        modifier = Modifier.semantics { contentDescription = "查看级别"; stateDescription = value.label },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value.label, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Icon(MiuixIcons.Basic.ArrowRight, null,
                Modifier.size(10.dp).graphicsLayer { rotationZ = 90f },
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
        }
    }
}
