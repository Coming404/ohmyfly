// 预览值为空时沿用升级前玻璃预设的表面、高光与模糊参数。
package com.ohmyfly.settings

internal data class GlassMaterialSettings(
    val secondLayerAlpha: Float? = null,
    val thirdLayerAlpha: Float? = null,
    val blurRadiusDp: Float? = null,
    val convexLensEnabled: Boolean = false,
    val convexLensProfile: GlassLensProfile = GlassLensProfile.Pill,
)
