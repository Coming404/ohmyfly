// Derived from Melox GlassContainerBlur.kt / LiquidGlassNavigationBar.kt, 49ee06a.
// GPL-3.0 for the former; see third-party/Melox-NOTICE.md. Application parameters are ours.
package com.ohmyfly.ui.glass

/** 导航光学参数沿用提交 3e9e878；模糊和色层来自实际 GlassMaterial。 */
internal data class GlassPreset(
    val saturation: Float = 1.5f,
    val brightness: Float = 0f,
    val lensHeight: Float = 24f,
    val lensAmount: Float = 24f,
    val highlightAlpha: Float = 0.75f,
    val highlightBlur: Float = 2f,
    val liquidResponse: Boolean = true,
)

internal fun glassPreset(): GlassPreset = GlassPreset()
