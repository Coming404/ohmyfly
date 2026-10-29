package com.ohmyfly.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ohmyfly.ui.glass.GlassShape

/** One circular boundary for clip, shadow, refraction and light at any measured size. */
internal object NavigationBarShape : GlassShape(0.dp) {
    override fun tile(size: Size, density: Density): Float = -size.minDimension * 0.5f

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        CircleShape.createOutline(size, layoutDirection, density)
}
