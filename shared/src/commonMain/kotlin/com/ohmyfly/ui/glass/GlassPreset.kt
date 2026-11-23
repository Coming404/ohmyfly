// 液态玻璃参数统一在此维护，设置页只覆盖已开放的可调项。
package com.ohmyfly.ui.glass

import com.ohmyfly.settings.MaterialMode
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

internal data class GlassPreset(
    val blur: Float,
    val saturation: Float,
    val brightness: Float,
    val surfaceAlpha: Float,
    val lensHeight: Float,
    val lensAmount: Float,
    val highlightAlpha: Float,
    val highlightBlur: Float,
    val liquidResponse: Boolean = false,
    val pressLensHeight: Float = LiquidGlassParameters.pressLensHeightDp,
    val pressLensAmount: Float = LiquidGlassParameters.pressLensAmountDp,
    val chromaticAberration: Float = LiquidGlassParameters.pressChromaticAberration,
    val samplingReachDp: Float = LiquidGlassParameters.samplingReachDp,
    val convexLensEnabled: Boolean = false,
)

/** 默认值按 Glasmio Clear 的无色填充与中性图像处理设置。 */
internal object LiquidGlassParameters {
    const val blurRadiusDp = 4f
    const val saturation = 1.06f
    const val brightness = 0f
    const val fillAlpha = 0f
    const val lensHeightDp = 24f
    const val lensAmountDp = 24f
    const val highlightAlpha = 0.75f
    const val highlightBlurDp = 2f
    const val pressLensHeightDp = 10f
    const val pressLensAmountDp = 14f
    const val depthEffectOnPress = true
    const val pressChromaticAberration = 0.5f
    const val samplingReachDp = 40f
    const val highlightWidthDp = 1f
    const val highlightColorAlpha = 0.12f
    const val primaryLightIntensity = 1f
    const val secondaryLightIntensity = 0.4f
    const val primaryLightX = 0.5f
    const val primaryLightY = 0.7f
    const val primaryLightZ = -0.05f
    const val secondaryLightX = 0.5f
    const val secondaryLightY = 0.8f
    const val secondaryLightZ = -0.5f
    const val dualPeakHighlight = true
    const val panelLightOffsetDegrees = -45f
    const val capsuleLightOffsetDegrees = 90f
}

internal fun glassPreset(mode: MaterialMode, dark: Boolean): GlassPreset = when (mode) {
    MaterialMode.LiquidGlass -> GlassPreset(
        blur = LiquidGlassParameters.blurRadiusDp,
        saturation = LiquidGlassParameters.saturation,
        brightness = LiquidGlassParameters.brightness,
        surfaceAlpha = LiquidGlassParameters.fillAlpha,
        lensHeight = LiquidGlassParameters.lensHeightDp,
        lensAmount = LiquidGlassParameters.lensAmountDp,
        highlightAlpha = LiquidGlassParameters.highlightAlpha,
        highlightBlur = LiquidGlassParameters.highlightBlurDp,
        liquidResponse = true,
        samplingReachDp = LiquidGlassParameters.samplingReachDp,
    )
    MaterialMode.Expressive -> GlassPreset(
        blur = 4f,
        saturation = 1f,
        brightness = 0f,
        surfaceAlpha = 0f,
        lensHeight = 0f,
        lensAmount = 0f,
        highlightAlpha = 0f,
        highlightBlur = 0f,
    )
}

internal fun glassEffectsEnabled(
    reduceTransparency: Boolean,
    shaderSupported: Boolean,
    systemReduceTransparency: Boolean,
): Boolean = !reduceTransparency && !systemReduceTransparency && shaderSupported

/** No silent unblurred transparency when RuntimeShader is unavailable. */
internal fun glassShaderSupported(): Boolean = isRuntimeShaderSupported()
