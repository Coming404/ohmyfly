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

internal fun BackdropEffectScope.glassEffects(preset: GlassPreset) {
    padding = maxOf(padding, preset.lensAmount.dp.toPx())
    colorControls(brightness = preset.brightness, saturation = preset.saturation)
    blur(preset.blur.dp.toPx())
    lens(preset.lensHeight.dp.toPx(), preset.lensAmount.dp.toPx(), depthEffect = false, chromaticAberration = 0f)
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
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = { glassEffects(preset) },
    highlight = { highlight.value },
    layerBlock = layerBlock,
    onDrawSurface = { drawRect(surface.copy(alpha = preset.surfaceAlpha)) },
)
