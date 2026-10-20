// Derived from Melox GlassContainerBlur.kt / LiquidGlassNavigationBar.kt, 49ee06a.
// GPL-3.0 for the former; see third-party/Melox-NOTICE.md. Application parameters are ours.
package dev.ohmydrift.ui.glass

import dev.ohmydrift.settings.MaterialMode
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

/** All presets use the same recording/effect path; radii and lens distances are dp. */
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
    MaterialMode.GaussianBlur -> GlassPreset(20f, 1f, 0f, if (dark) 0.28f else 0.22f, 8f, 2f, 0.12f, 2f)
    MaterialMode.HighlightGlass -> GlassPreset(4f, 1.15f, 0f, if (dark) 0.22f else 0.16f, 16f, 6f, 1f, 2f)
    MaterialMode.SoftGlass -> GlassPreset(24f, 1.05f, if (dark) 0f else 0.03f, if (dark) 0.36f else 0.30f, 16f, 4f, 0.45f, 4f)
    MaterialMode.LiquidGlass -> GlassPreset(4f, 1.5f, 0f, 0.4f, 24f, 24f, 0.75f, 2f, liquidResponse = true)
}

internal fun glassEffectsEnabled(
    reduceTransparency: Boolean,
    shaderSupported: Boolean,
    systemReduceTransparency: Boolean,
): Boolean = !reduceTransparency && !systemReduceTransparency && shaderSupported

/** No silent unblurred transparency when RuntimeShader is unavailable. */
internal fun glassShaderSupported(): Boolean = isRuntimeShaderSupported()
