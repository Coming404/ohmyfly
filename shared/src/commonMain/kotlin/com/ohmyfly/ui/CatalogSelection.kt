// 顶部保留取消与全选；底部浮动工具栏只承载当前选择的批量操作。
package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    FloatingToolbar(
        color = Color.Transparent,
        shadowElevation = 0.dp,
        outSidePadding = PaddingValues(0.dp),
        cornerRadius = AppCorners.Toolbar,
    ) {
        Row(
            modifier = modifier.widthIn(max = 360.dp).heightIn(min = ChromeMetrics.TopBarHeight)
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(ChromeMetrics.ActionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
    CatalogActionToolbar(modifier) {
        SelectionTopAction(true, "取消", DriftIcons.Clear, onCancel)
        SelectionTopAction(!selecting, if (selecting) "读取中" else selectAllLabel,
            DriftIcons.Check, onSelectAll)
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
    val color = if (enabled) tint else MiuixTheme.colorScheme.disabledOnSurface
    IconButton(
        onClick = hapticClick(onClick),
        enabled = enabled,
        modifier = Modifier.clip(AppCorners.shape(AppCorners.Toolbar)),
        minWidth = ChromeMetrics.ToolbarButtonSize,
        minHeight = ChromeMetrics.ToolbarButtonSize,
        backgroundColor = Color.Transparent,
        cornerRadius = AppCorners.Toolbar,
    ) {
        Row(
            Modifier.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChromeMetrics.ActionGap),
        ) {
            Icon(icon, null, Modifier.size(ChromeMetrics.ToolbarIconSize), tint = color)
            Text(label, color = color, fontSize = ChromeMetrics.ToolbarLabelSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** 顶部动作横排显示，保持与普通顶栏按钮相近的高度。 */
@Composable
private fun SelectionTopAction(enabled: Boolean, label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface
    IconButton(onClick = hapticClick(onClick), enabled = enabled,
        modifier = Modifier.clip(AppCorners.shape(AppCorners.Toolbar)),
        minWidth = ChromeMetrics.ToolbarButtonSize,
        minHeight = ChromeMetrics.ToolbarButtonSize,
        backgroundColor = Color.Transparent,
        cornerRadius = AppCorners.Toolbar,
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChromeMetrics.ActionGap)) {
            Icon(icon, null, Modifier.size(ChromeMetrics.ToolbarIconSize), tint = color)
            Text(label, color = color, fontSize = ChromeMetrics.ToolbarLabelSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
