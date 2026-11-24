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
    // Melox reserves the full press/lens reach before blurring. Miuix blur(4dp) already
    // supplies sigma=4dp*0.45 on each platform; do not reuse Android's BlurEffect conversion on iOS.
    padding = maxOf(padding, preset.effectSamplingReachDp.dp.toPx())
    colorControls(brightness = preset.brightness, saturation = preset.saturation)
    blur(preset.blur.dp.toPx())
    lens(
        if (preset.convexLensEnabled) preset.convexLensProfile.bezelWidthDp.dp.toPx()
        else (preset.lensHeight + preset.pressLensHeight * press).dp.toPx(),
        if (preset.convexLensEnabled) preset.convexLensProfile.thicknessDp.dp.toPx()
        else (preset.lensAmount + preset.pressLensAmount * press).dp.toPx(),
        depthEffect = preset.convexLensEnabled || (LiquidGlassParameters.depthEffectOnPress && press > 0f),
        chromaticAberration = preset.chromaticAberration * press,
        convexProfile = preset.convexLensProfile.takeIf { preset.convexLensEnabled },
        glassThicknessDp = preset.convexLensProfile.thicknessDp,
        refractionScale = LiquidGlassParameters.goldenRefractionScale,
    )
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
    onDrawSurface = { drawRect(surface.copy(alpha = preset.surfaceAlpha)) },
)
