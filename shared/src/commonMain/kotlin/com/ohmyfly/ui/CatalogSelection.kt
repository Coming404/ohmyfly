// 顶部保留取消与全选；底部浮动工具栏只承载当前选择的批量操作。
package com.ohmyfly.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.ui.settings.MirrorIcons
import com.ohmyfly.ui.settings.SettingsIcons
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class CatalogSelectionMode { Nodes, Towers }

@Composable
internal fun CatalogSelectionToolbar(
    mode: CatalogSelectionMode,
    modifier: Modifier,
    enabled: Boolean,
    onSpeed: () -> Unit,
    onToggle: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSync: () -> Unit,
) {
    FloatingToolbar(modifier = modifier, color = Color.Transparent,
        outSidePadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (mode == CatalogSelectionMode.Nodes) {
                SelectionToolbarAction(enabled, "测速", DriftIcons.Speed, onSpeed)
                SelectionToolbarAction(enabled, "启停", MirrorIcons.Power, onToggle)
                SelectionToolbarAction(enabled, "分组", DriftIcons.Nodes, onMove)
                SelectionToolbarAction(enabled, "分享", SettingsIcons.Backup, onShare)
                SelectionToolbarAction(enabled, "删除", MirrorIcons.Trash, onDelete, tint = MiuixTheme.colorScheme.error)
            } else {
                SelectionToolbarAction(enabled, "同步", SettingsIcons.Retry, onSync)
                SelectionToolbarAction(enabled, "删除", MirrorIcons.Trash, onDelete, tint = MiuixTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun CatalogSelectionTopToolbar(
    modifier: Modifier,
    selecting: Boolean,
    selectAllLabel: String,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
) {
    FloatingToolbar(modifier = modifier, color = Color.Transparent,
        outSidePadding = PaddingValues(0.dp, 4.dp)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SelectionToolbarAction(true, "取消", DriftIcons.Clear, onCancel)
            SelectionToolbarAction(!selecting, if (selecting) "读取中" else selectAllLabel,
                DriftIcons.Check, onSelectAll)
        }
    }
}

@Composable
private fun SelectionToolbarAction(
    enabled: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurface,
) {
    top.yukonga.miuix.kmp.basic.IconButton(onClick = hapticClick(onClick), enabled = enabled,
        modifier = Modifier.width(56.dp).heightIn(min = 56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, label, Modifier.size(22.dp), tint = tint)
            Text(label, color = tint, fontSize = 11.sp, maxLines = 1)
        }
    }
}
