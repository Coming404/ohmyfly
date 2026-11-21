// 绘制、折射、边缘高光和选中胶囊共用一条 G2 胶囊轮廓。
package com.ohmyfly.ui

import androidx.compose.foundation.shape.CornerSize
import top.yukonga.miuix.kmp.material.GlassShape

/** 胶囊不使用连续圆角修饰；普通内容圆角仍由 AppCorners 提供 G2 曲线。 */
internal val NavigationBarShape = GlassShape(
    CornerSize(50), CornerSize(50), CornerSize(50), CornerSize(50), smoothing = 1f,
)
