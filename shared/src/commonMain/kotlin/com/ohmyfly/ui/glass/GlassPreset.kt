// 液态玻璃参数统一在此维护，设置页只覆盖已开放的可调项。
package com.ohmyfly.ui.glass

import com.ohmyfly.settings.MaterialMode
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

internal data class GlassPreset(
    val blur: Float,
    val saturation: Float,
    val brightness: Float,
    val surfaceAlpha: Float,
    val highlightAlpha: Float,
    val highlightBlur: Float,
    val liquidResponse: Boolean = false,
    val chromaticAberration: Float = LiquidGlassParameters.pressChromaticAberration,
    val samplingReachDp: Float = LiquidGlassParameters.samplingReachDp,
    val convexLensEnabled: Boolean = false,
    val convexLensProfile: com.ohmyfly.settings.GlassLensProfile = com.ohmyfly.settings.GlassLensProfile.Pill,
) {
    val effectSamplingReachDp: Float
        get() = maxOf(
            samplingReachDp,
            if (convexLensEnabled) {
                (convexLensProfile.bezelWidthDp + convexLensProfile.thicknessDp) *
                    convexLensProfile.refractionScale + blur + 2f
            } else samplingReachDp,
        )
}

/** 默认液态玻璃配方取自 liquid-glass 的 pill、control、card presets。 */
internal object LiquidGlassParameters {
    const val blurRadiusDp = 0.2f
    const val saturation = 1.3f
    const val brightness = 0f
    const val fillAlphaLight = 0.25f
    const val fillAlphaDark = 0.03f
    const val highlightAlpha = 0.75f
    const val highlightBlurDp = 2f
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
}

internal fun glassPreset(mode: MaterialMode, dark: Boolean): GlassPreset = when (mode) {
    MaterialMode.LiquidGlass -> GlassPreset(
        blur = LiquidGlassParameters.blurRadiusDp,
        saturation = LiquidGlassParameters.saturation,
        brightness = LiquidGlassParameters.brightness,
        surfaceAlpha = if (dark) LiquidGlassParameters.fillAlphaDark else LiquidGlassParameters.fillAlphaLight,
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
