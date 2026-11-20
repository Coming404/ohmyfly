// 导航与独立控件共用尺寸，避免普通态和选择态出现不同的点击区域。
package com.ohmyfly.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object ChromeMetrics {
    val ControlSize = 44.dp
    val IconSize = 20.dp
    val TitleSize = 17.sp
    val ActionTextSize = 14.sp
    val ActionGap = 4.dp
    val TitleInset = 14.dp
    val OutlineWidth = 0.5.dp
    const val OutlineAlpha = 0.10f
    val SelectionHeight = 60.dp
    val SelectionWidth = 268.dp
}
