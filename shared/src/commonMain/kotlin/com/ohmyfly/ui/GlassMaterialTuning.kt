// Glasmio Clear 可映射的通用表面参数：零色片填充、0.75 dp 模糊。
package com.ohmyfly.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ohmyfly.settings.GlassMaterialSettings
import com.ohmyfly.ui.glass.ClearBlurRadiusDp
import top.yukonga.miuix.kmp.material.GlassColorBlendMode
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassMaterial

/** Clear 配方的色片透明度和模糊基线，再叠加用户持久化覆写。 */
internal fun GlassMaterial.withSettings(settings: GlassMaterialSettings): GlassMaterial = copy(
    blurRadius = settings.blurRadiusDp?.dp ?: ClearBlurRadiusDp.dp,
    first = first.copy(color = Color.Transparent, mode = GlassColorBlendMode.SrcOver),
    second = second?.withAlpha(settings.secondLayerAlpha ?: 0f),
    third = third?.withAlpha(settings.thirdLayerAlpha ?: 0f),
)

/** 保留各层原色及顺序，只覆盖实际 Alpha。 */
private fun GlassColorLayer.withAlpha(alpha: Float): GlassColorLayer =
    copy(color = color.copy(alpha = alpha.coerceIn(0f, 1f)))
