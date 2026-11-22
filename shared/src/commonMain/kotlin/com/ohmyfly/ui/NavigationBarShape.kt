package com.ohmyfly.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.ohmyfly.ui.glass.GlassShape

/** 导航、标题与圆形动作共用半短边半径，裁剪和光学距离场一致。 */
internal object NavigationBarShape : GlassShape() {
    override val surface: androidx.compose.ui.graphics.Shape = com.ohmyfly.shapes.AppCorners.Capsule
    override fun tile(size: Size, density: Density): Float = -size.minDimension * 0.5f
}
