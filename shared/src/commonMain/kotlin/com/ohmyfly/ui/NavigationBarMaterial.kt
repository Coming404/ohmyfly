// 导航材质沿用 3e9e878 的单层轻染色，保留当前共享滑块覆盖。
package com.ohmyfly.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ohmyfly.settings.GlassMaterialSettings
import top.yukonga.miuix.kmp.material.GlassColorBlendMode
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 以旧导航实际参数作为滑块基线，第二层承载旧表面染色，第三层可由用户启用。 */
@Composable
internal fun navigationBarBaseMaterial(dark: Boolean): GlassMaterial {
    val surface = MiuixTheme.colorScheme.surface
    val tint = if (dark) surface else Color.White
    val tintAlpha = if (dark) 0.10f else 0.30f
    val thirdAlpha = 0f
    val base = remember(dark, tint, tintAlpha, thirdAlpha) {
        GlassMaterial(
            blurRadius = 4.dp,
            first = GlassColorLayer(Color.Transparent, GlassColorBlendMode.SrcOver),
            second = GlassColorLayer(tint.copy(alpha = tintAlpha), GlassColorBlendMode.SrcOver),
            third = GlassColorLayer(Color.White.copy(alpha = thirdAlpha), GlassColorBlendMode.SrcOver),
        )
    }
    return base
}

/** 导航栏读取同一持久化材质覆盖值。 */
@Composable
internal fun navigationBarMaterial(dark: Boolean): GlassMaterial {
    val base = navigationBarBaseMaterial(dark)
    val settings = LocalChromeAppearance.current.materialSettings
    return remember(base, settings) { base.withSettings(settings) }
}
