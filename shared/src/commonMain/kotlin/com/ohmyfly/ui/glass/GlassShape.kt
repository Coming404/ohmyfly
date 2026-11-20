// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// 玻璃胶囊共用裁剪、折射和高光边界。
package com.ohmyfly.ui.glass

// 应用内部连续曲率由统一形状模块管理，玻璃层只保留胶囊光学边界。
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** 玻璃光学层只使用真胶囊；内容圆角由 AppCorners 提供连续曲率。 */
internal abstract class GlassShape : Shape {
    abstract val surface: Shape
    abstract fun tile(size: Size, density: Density): Float

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        surface.createOutline(size, layoutDirection, density)
}

/** 高光和折射共用真胶囊的距离场，与表面裁剪同形。 */
internal const val GLASS_BOUNDARY_SDF = """
float3 glassBoundary(float2 p, float2 halfSize, float tile) {
    float radius = -tile;
    float2 extent = halfSize - radius;
    float2 delta = p - clamp(p, -extent, extent);
    float d = length(delta);
    return float3(delta / max(d, 0.001), d - radius);
}
"""
