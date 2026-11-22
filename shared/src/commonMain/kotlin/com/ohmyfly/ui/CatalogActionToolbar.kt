// 塔台与航班的普通顶栏动作共用横排胶囊，材质由页面传入。
package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 控件统一为 44 dp 点击区，工具栏宽度由动作数量自然撑开。 */
@Composable
internal fun CatalogActionToolbar(
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    FloatingToolbar(
        color = Color.Transparent,
        shadowElevation = 0.dp,
        outSidePadding = PaddingValues(0.dp), cornerRadius = AppCorners.Toolbar,
    ) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(ChromeMetrics.ActionGap),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 工具栏内只绘制图标和按压反馈，避免每个动作叠加独立材质。 */
@Composable
internal fun CatalogToolbarAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TooltipBox(text = label) {
        IconButton(
            onClick = hapticClick(onClick),
            enabled = enabled,
            modifier = Modifier.size(ChromeMetrics.ControlSize).clip(AppCorners.shape(AppCorners.Toolbar)),
            backgroundColor = Color.Transparent, cornerRadius = AppCorners.Toolbar,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(ChromeMetrics.IconSize),
                tint = if (enabled) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.disabledOnSurface,
            )
        }
    }
}
