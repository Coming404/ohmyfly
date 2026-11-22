// Derived from Melox GlassContainerBlur.kt / LiquidGlassNavigationBar.kt, 49ee06a.
// GPL-3.0 for the former; see third-party/Melox-NOTICE.md. Application parameters are ours.
package com.ohmyfly.ui.glass

import com.ohmyfly.settings.MaterialMode
import com.kyant.backdrop.isRuntimeShaderSupported

/** 材质共享设置参数，半径和折射距离均为 dp。 */
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
)

internal fun glassPreset(mode: MaterialMode, dark: Boolean): GlassPreset = when (mode) {
    MaterialMode.LiquidGlass -> GlassPreset(
        4f, 1.5f, 0f, if (dark) 0.10f else 0.30f, 24f, 24f, 1f, 0.25f,
        liquidResponse = true,
    )
    MaterialMode.Expressive -> GlassPreset(4f, 1f, 0f, 0f, 0f, 0f, 0f, 0f)
}

internal fun glassEffectsEnabled(
    reduceTransparency: Boolean,
    shaderSupported: Boolean,
    systemReduceTransparency: Boolean,
): Boolean = !reduceTransparency && !systemReduceTransparency && shaderSupported

/** No silent unblurred transparency when RuntimeShader is unavailable. */
internal fun glassShaderSupported(): Boolean = isRuntimeShaderSupported()
