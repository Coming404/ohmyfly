// 目录选择模式统一使用 Miuix 浮动工具栏；页面本身只负责提供业务动作。
package com.ohmyfly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.ui.settings.MirrorIcons
import com.ohmyfly.ui.settings.SettingsIcons
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class CatalogSelectionMode { Nodes, Towers }

@Composable
internal fun CatalogSelectionToolbar(
    mode: CatalogSelectionMode,
    enabled: Boolean = true,
    onSpeed: () -> Unit,
    onToggle: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSync: () -> Unit,
) {
    FloatingToolbar {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (mode == CatalogSelectionMode.Nodes) {
                SelectionToolbarAction(enabled, "测速", DriftIcons.Speed, onSpeed)
                SelectionToolbarAction(enabled, "启停", MirrorIcons.Power, onToggle)
                SelectionToolbarAction(enabled, "分组", DriftIcons.Nodes, onMove)
                SelectionToolbarAction(enabled, "分享", SettingsIcons.Backup, onShare)
                SelectionToolbarAction(enabled, "删除", MirrorIcons.Trash, onDelete,
                    tint = MiuixTheme.colorScheme.error)
            } else {
                SelectionToolbarAction(enabled, "同步", SettingsIcons.Retry, onSync)
                SelectionToolbarAction(enabled, "删除", MirrorIcons.Trash, onDelete,
                    tint = MiuixTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SelectionToolbarAction(enabled: Boolean, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurface) {
    top.yukonga.miuix.kmp.basic.IconButton(onClick = hapticClick(onClick), enabled = enabled, modifier = Modifier.size(56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = tint)
            Text(label, color = tint, fontSize = 11.sp, maxLines = 1)
        }
    }
}
