// Derived from Melox GlassContainerBlur.kt (no per-file license header),
// Melox 49ee06aa1f01a67eb5ea1d5cc7307ddfd571a534, GPL-3.0; see third-party/Melox-NOTICE.md.
package dev.ohmydrift.ui.glass

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
    padding = maxOf(padding, (if (preset.liquidResponse) 40f else preset.lensAmount).dp.toPx())
    colorControls(brightness = preset.brightness, saturation = preset.saturation)
    blur(preset.blur.dp.toPx())
    lens(
        (preset.lensHeight + 10f * press).dp.toPx(),
        (preset.lensAmount + 14f * press).dp.toPx(),
        depthEffect = press > 0f,
        chromaticAberration = 0.5f * press,
    )
}

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
