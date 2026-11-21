// 圆形动作与导航胶囊共用同一轮廓，绘制、折射及点击边界保持一致。
package com.ohmyfly.ui

import androidx.compose.foundation.shape.CornerSize
import top.yukonga.miuix.kmp.material.GlassShape

/** 胶囊不使用连续圆角修饰；普通内容圆角仍由 AppCorners 提供 G2 曲线。 */
internal val NavigationBarShape = GlassShape(
    CornerSize(50), CornerSize(50), CornerSize(50), CornerSize(50), smoothing = 0f,
)
