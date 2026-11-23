// 导航与独立控件共用尺寸，避免普通态和选择态出现不同的点击区域。
package com.ohmyfly.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object ChromeMetrics {
    val ControlSize = 44.dp
    val ToolbarButtonSize = 44.dp
    val ToolbarIconSize = 20.dp
    val ToolbarLabelSize = 13.sp
    val TitleSize = 16.sp
    val ActionGap = 6.dp
    val TitleInset = 14.dp
    val TopBarHeight = 54.dp
    val TitleCapsuleHeight = ControlSize
    val NavigationHeight = 62.dp
    val NavigationHeight = 62.dp
    val NavigationBottomInset = 21.dp
    val NavigationIndicatorHeight = 56.dp
    val NavigationIndicatorInset = 3.dp
    val OutlineWidth = 0.5.dp
    const val OutlineAlpha = 0.10f
}
