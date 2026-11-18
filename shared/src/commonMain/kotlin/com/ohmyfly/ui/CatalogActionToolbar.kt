// 塔台与航班的普通顶栏动作共用横排胶囊，材质由页面传入。
package com.ohmyfly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 材质只绘制一次；48 dp 点击区加两侧留白形成 52 dp 高的工具栏。 */
@Composable
internal fun CatalogActionToolbar(
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    FloatingToolbar(
        color = Color.Transparent,
        shadowElevation = 0.dp,
        outSidePadding = PaddingValues(0.dp),
    ) {
        Row(
            modifier = modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            modifier = Modifier.size(48.dp),
            backgroundColor = Color.Transparent,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.disabledOnSurface,
            )
        }
    }
}
