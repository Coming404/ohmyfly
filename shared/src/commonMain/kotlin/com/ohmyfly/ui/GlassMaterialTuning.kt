// 将三个通用预览滑块映射到升级前玻璃引擎的现有参数。
package com.ohmyfly.ui

import com.ohmyfly.settings.GlassMaterialSettings
import com.ohmyfly.ui.glass.GlassPreset

/** 色片透明度控制表面填充，高光透明度控制旧引擎的扩散高光。 */
internal fun GlassPreset.withSettings(settings: GlassMaterialSettings, dark: Boolean): GlassPreset = copy(
    surfaceAlpha = settings.secondLayerAlpha ?: if (settings.convexLensEnabled && liquidResponse) {
        if (dark) settings.convexLensProfile.darkFillAlpha else settings.convexLensProfile.lightFillAlpha
    } else surfaceAlpha,
    highlightAlpha = settings.thirdLayerAlpha ?: highlightAlpha,
    blur = settings.blurRadiusDp ?: if (settings.convexLensEnabled && liquidResponse) settings.convexLensProfile.blur else blur,
    convexLensEnabled = settings.convexLensEnabled,
    convexLensProfile = settings.convexLensProfile,
)
