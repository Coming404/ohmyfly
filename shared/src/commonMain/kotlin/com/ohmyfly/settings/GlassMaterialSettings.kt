// 液态玻璃覆盖值为空时沿用当前组件的 Miuix 原生配方。
package com.ohmyfly.settings

internal data class GlassMaterialSettings(
    val secondLayerAlpha: Float? = null,
    val thirdLayerAlpha: Float? = null,
    val blurRadiusDp: Float? = null,
)
