// Derived from Melox GlassContainerBlur.kt (no per-file license header),
// Melox 49ee06aa1f01a67eb5ea1d5cc7307ddfd571a534, GPL-3.0; see third-party/Melox-NOTICE.md.
package com.ohmyfly.ui.glass

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight

internal fun BackdropEffectScope.glassEffects(preset: GlassPreset, pressProgress: Float = 0f) {
    val press = if (preset.liquidResponse) pressProgress.coerceIn(0f, 1f) else 0f
    padding = maxOf(padding, preset.effectSamplingReachDp.dp.toPx())
    colorControls(brightness = preset.brightness, saturation = preset.saturation)
    blur(preset.effectiveBlurDp.dp.toPx())
    if (preset.convexLensEnabled) {
        val profile = preset.convexLensProfile
        lens(
            refractionHeight = profile.bezelWidthDp.dp.toPx(),
            refractionAmount = size.minDimension * 0.5f * profile.interiorRefractionGain *
                (1f + LiquidGlassParameters.pressMagnification * press),
            depthEffect = true,
            chromaticAberration = preset.effectiveChromaticAberration,
            convexProfile = profile,
            glassThicknessDp = profile.effectiveThicknessDp,
            refractionScale = profile.refractionScale,
        )
    } else if (preset.liquidResponse) {
        // 常态保留背景折射；按压镜片由选中区域单独采样，避免整条导航一起放大。
        lens(
            refractionHeight = LiquidGlassParameters.restingRefractionDp.dp.toPx(),
            refractionAmount = LiquidGlassParameters.restingRefractionDp.dp.toPx(),
        )
    }
}

/** Light glass is tinted white so it can only lift content; Miuix light surface (F7F7F7) grays white cards. */
internal fun glassTint(surface: Color, dark: Boolean): Color = if (dark) surface else Color.White

internal fun Modifier.glassSurface(
    backdrop: Backdrop,
    shape: GlassShape,
    preset: GlassPreset,
    surface: Color,
    highlight: State<Highlight>,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    pressProgress: () -> Float = { 0f },
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = { glassEffects(preset, pressProgress()) },
    highlight = { highlight.value },
    layerBlock = layerBlock,
    onDrawSurface = {
        drawRect((preset.effectiveSurfaceColor ?: surface).copy(alpha = preset.effectiveSurfaceAlpha))
    },
)
