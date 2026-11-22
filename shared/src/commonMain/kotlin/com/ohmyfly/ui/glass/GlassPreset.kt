// Derived from Melox GlassContainerBlur.kt / LiquidGlassNavigationBar.kt, 49ee06a.
// GPL-3.0 for the former; see third-party/Melox-NOTICE.md. Application parameters are ours.
package com.ohmyfly.ui.glass

internal const val ClearBlurRadiusDp = 0.75f
internal const val ClearFaceSaturation = 1.06f
internal const val ClearBlackPoint = 0.125f
internal const val ClearWhitePoint = 1.08f
internal const val ClearMaximumLuma = 1f

internal data class GlassPreset(
    val saturation: Float = 1f,
    val brightness: Float = 0f,
    val blurDp: Float = 4f,
    val innerLensHeight: Float = 24f,
    val innerLensAmount: Float = 24f,
    val highlightAlpha: Float = 0.75f,
    val highlightStrokeWidthDp: Float = 1f,
    val highlightBlurRadiusDp: Float = 2f,
    val highlightColorAlpha: Float = 0.12f,
    val liquidResponse: Boolean = true,
)

internal fun glassPreset(): GlassPreset = GlassPreset()
