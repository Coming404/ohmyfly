// Derived from Melox GlassContainerBlur.kt, 49ee06a; see third-party/Melox-NOTICE.md.
package com.ohmyfly.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.blendColors
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.noiseDither
import top.yukonga.miuix.kmp.material.GlassColorBlendMode
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.drawGlassRim

/** 复用 Miuix 玻璃材料的实际模糊与色片值，避免导航另有一套固定参数。 */
/** 颜色层列表在组合阶段缓存，避免按压动画逐帧创建对象。 */
@Composable
internal fun rememberGlassMaterialColors(material: GlassMaterial): BlurColors = remember(material) {
    material.toBlurColors()
}

/** 玻璃色片保持原颜色与模式，按 Miuix 背景混色契约转换。 */
internal fun GlassMaterial.toBlurColors(): BlurColors = BlurColors(
    blendColors = listOfNotNull(first.toBlendEntry(), second?.toBlendEntry(), third?.toBlendEntry()),
)

private fun GlassColorLayer.toBlendEntry(): BlendColorEntry = BlendColorEntry(color, mode.toBlurMode())

private fun GlassColorBlendMode.toBlurMode(): BlurBlendMode = when (this) {
    GlassColorBlendMode.SrcOver -> BlurBlendMode.SrcOver
    GlassColorBlendMode.PlusDarker -> BlurBlendMode.PlusDarker
    GlassColorBlendMode.PlusLighter -> BlurBlendMode.PlusLighter
    GlassColorBlendMode.SoftLight -> BlurBlendMode.SoftLight
    GlassColorBlendMode.HardLight -> BlurBlendMode.HardLight
    GlassColorBlendMode.Overlay -> BlurBlendMode.Overlay
    GlassColorBlendMode.Luminosity -> BlurBlendMode.Luminosity
    GlassColorBlendMode.ColorDodge -> BlurBlendMode.ColorDodge
    GlassColorBlendMode.ColorBurn -> BlurBlendMode.ColorBurn
}

internal fun BackdropEffectScope.glassEffects(
    preset: GlassPreset,
    material: GlassMaterial,
    colors: BlurColors,
    pressProgress: Float = 0f,
) {
    val press = if (preset.liquidResponse) pressProgress.coerceIn(0f, 1f) else 0f
    padding = maxOf(padding, 40.dp.toPx())
    noiseDither(0f)
    colorControls(brightness = preset.brightness, saturation = preset.saturation)
    blur(material.blurRadius.toPx())
    blendColors(colors)
    lens(
        10.dp.toPx() * press,
        14.dp.toPx() * press,
        depthEffect = press > 0f,
        chromaticAberration = 0.5f * press,
    )
}

/** 背景模糊和色层由材质设置驱动，G2 轮廓共用于裁剪与边缘光。 */
internal fun Modifier.glassSurface(
    backdrop: Backdrop,
    shape: GlassShape,
    preset: GlassPreset,
    material: GlassMaterial,
    colors: BlurColors,
    surface: Color,
    style: GlassStyle,
    alpha: Float = 1f,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    pressProgress: () -> Float = { 0f },
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = { glassEffects(preset, material, colors, pressProgress()) },
    layerBlock = layerBlock,
    onDrawFront = { drawGlassRim(shape, style, surface, alpha) },
)
