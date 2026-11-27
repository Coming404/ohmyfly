// 将三个通用预览滑块映射到升级前玻璃引擎的现有参数。
package com.ohmyfly.ui

import com.ohmyfly.settings.GlassMaterialSettings
import com.ohmyfly.ui.glass.GlassPreset
import com.ohmyfly.ui.glass.LiquidGlassParameters

/** 色片透明度控制表面填充，高光透明度控制旧引擎的扩散高光。 */
internal fun GlassPreset.withSettings(settings: GlassMaterialSettings, dark: Boolean): GlassPreset {
    val convex = settings.convexLensEnabled && liquidResponse
    return copy(
        surfaceAlpha = settings.secondLayerAlpha ?: if (convex) LiquidGlassParameters.convexFillAlpha else surfaceAlpha,
        highlightAlpha = settings.thirdLayerAlpha ?: highlightAlpha,
        blur = settings.blurRadiusDp ?: if (convex) settings.convexLensProfile.recipeBlurDp else blur,
        brightness = if (convex) LiquidGlassParameters.convexBrightness else brightness,
        saturation = if (convex) LiquidGlassParameters.convexSaturation else saturation,
        convexLensEnabled = convex,
        convexLensProfile = settings.convexLensProfile,
        blurIsExplicit = settings.blurRadiusDp != null,
        surfaceAlphaIsExplicit = settings.secondLayerAlpha != null,
        fillColor = if (convex) LiquidGlassParameters.convexFillColor else null,
        lightAngleDegrees = if (convex) LiquidGlassParameters.convexLightAngleDegrees else null,
        directLightIntensity = if (convex) LiquidGlassParameters.convexDirectLightIntensity else LiquidGlassParameters.primaryLightIntensity,
        ambientLightIntensity = if (convex) LiquidGlassParameters.convexAmbientLightIntensity else LiquidGlassParameters.secondaryLightIntensity,
    )
}
