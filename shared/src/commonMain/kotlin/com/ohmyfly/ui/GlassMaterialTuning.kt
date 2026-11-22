// 液态玻璃参数以当前组件默认值为基线，显式覆盖时使用实际 alpha 和 dp 值。
package com.ohmyfly.ui

import androidx.compose.ui.unit.dp
import com.ohmyfly.settings.GlassMaterialSettings
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassMaterial

/** 未设置参数时保留各组件、各主题原有配方。 */
internal fun GlassMaterial.withSettings(settings: GlassMaterialSettings): GlassMaterial {
    if (settings.secondLayerAlpha == null && settings.thirdLayerAlpha == null && settings.blurRadiusDp == null) {
        return this
    }
    return copy(
        blurRadius = settings.blurRadiusDp?.dp ?: blurRadius,
        second = settings.secondLayerAlpha?.let { second?.withAlpha(it) } ?: second,
        third = settings.thirdLayerAlpha?.let { third?.withAlpha(it) } ?: third,
    )
}

/** 直接替换色片 alpha，不改变颜色混合模式。 */
private fun GlassColorLayer.withAlpha(alpha: Float): GlassColorLayer =
    copy(color = color.copy(alpha = alpha.coerceIn(0f, 1f)))
