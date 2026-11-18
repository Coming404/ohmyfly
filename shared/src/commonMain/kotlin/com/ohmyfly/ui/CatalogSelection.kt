// 顶部保留取消与全选；底部浮动工具栏只承载当前选择的批量操作。
package com.ohmyfly.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.IconButton
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
    // 材质只绘制在内容表面，FloatingToolbar 保留定位和默认外边距，避免双层背景遮住玻璃。
    FloatingToolbar(color = Color.Transparent, shadowElevation = 0.dp) {
        Row(modifier.horizontalScroll(rememberScrollState()).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
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
    FloatingToolbar(color = Color.Transparent, shadowElevation = 0.dp, outSidePadding = PaddingValues(0.dp)) {
        Row(modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SelectionTopAction(true, "取消", DriftIcons.Clear, onCancel)
            SelectionTopAction(!selecting, if (selecting) "读取中" else selectAllLabel,
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
    IconButton(onClick = hapticClick(onClick), enabled = enabled, modifier = Modifier.size(48.dp),
        backgroundColor = Color.Transparent, cornerRadius = 16.dp) {
        val color = if (enabled) tint else MiuixTheme.colorScheme.disabledOnSurface
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = color)
            Text(label, color = color, fontSize = 11.sp, maxLines = 1)
        }
    }
}

/** 顶部动作横排显示，保持与普通顶栏按钮相近的高度。 */
@Composable
private fun SelectionTopAction(enabled: Boolean, label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val color = if (enabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.disabledOnSurface
    IconButton(onClick = hapticClick(onClick), enabled = enabled, minWidth = 0.dp, minHeight = 40.dp,
        backgroundColor = Color.Transparent, cornerRadius = 18.dp) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Text(label, color = color, fontSize = 14.sp, maxLines = 1)
        }
    }
}
