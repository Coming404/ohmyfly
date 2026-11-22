// Derived from Melox GlassContainerBlur.kt, 49ee06a; see third-party/Melox-NOTICE.md.
package com.ohmyfly.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect
import top.yukonga.miuix.kmp.material.GlassColorBlendMode
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.RuntimeShader
import top.yukonga.miuix.kmp.material.asBrush
import top.yukonga.miuix.kmp.material.agsl.MaterialAgsl
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.material.setSilhouetteUniforms

/** 颜色层列表在组合阶段缓存，避免按压动画逐帧创建对象。 */
@Composable
internal fun rememberGlassMaterialColors(material: GlassMaterial): BlurColors = remember(material) {
    material.toBlurColors()
}

/** 玻璃色片保持原颜色与模式，按 Miuix 背景混色契约转换。 */
/** Beta03 移除了 Highlight 插槽；此处以 G2 距离场绘制扩散 BloomStroke。 */
@Composable
internal fun rememberClearBloomShader(enabled: Boolean): RuntimeShader? = remember(enabled) {
    if (enabled && isRuntimeShaderSupported()) RuntimeShader(ClearBloomShader) else null
}

internal fun DrawScope.drawClearBloom(
    shape: GlassShape,
    style: GlassStyle,
    shader: RuntimeShader,
    preset: GlassPreset,
    progress: Float,
) {
    val alpha = preset.highlightAlpha * progress.coerceIn(0f, 1f)
    if (alpha <= 0f || size.width <= 0f || size.height <= 0f) return
    shader.setSilhouetteUniforms(shape, layoutDirection, size, this)
    shader.setFloatUniform("strokeWidth", preset.highlightStrokeWidthDp.dp.toPx())
    shader.setFloatUniform("innerBlurRadius", preset.highlightBlurRadiusDp.dp.toPx())
    shader.setFloatUniform("highlightAlpha", alpha)
    shader.setColorUniform("strokeColor", Color.White)
    shader.setFloatUniform("strokeAlphaMul", preset.highlightColorAlpha)
    shader.setFloatUniform("lightDir1", style.light.directionX, style.light.directionY, style.light.directionZ)
    shader.setFloatUniform("lightIntensity1", style.light.intensity)
    shader.setFloatUniform("lightDir2", -style.light.directionX, -style.light.directionY, style.light.directionZ)
    shader.setFloatUniform("lightIntensity2", style.light.oppositeIntensity)
    drawRect(brush = shader.asBrush(), blendMode = BlendMode.Plus)
}

private val ClearBloomShader = """
uniform float2 in_size;
uniform float4 in_radii;
uniform float in_smoothing;
uniform float in_g2Scale;
uniform float4 in_g2Curves[24];
uniform float strokeWidth;
uniform float innerBlurRadius;
uniform float highlightAlpha;
layout(color) uniform half4 strokeColor;
uniform float strokeAlphaMul;
uniform float3 lightDir1;
uniform float lightIntensity1;
uniform float3 lightDir2;
uniform float lightIntensity2;

${MaterialAgsl.SilhouetteSdf}

float2 clearBloomGradient(float2 p) {
    return float2(
        silhouetteSdf(p + float2(0.5, 0.0)) - silhouetteSdf(p - float2(0.5, 0.0)),
        silhouetteSdf(p + float2(0.0, 0.5)) - silhouetteSdf(p - float2(0.0, 0.5))
    );
}

half4 main(float2 coord) {
    float sdf = silhouetteSdf(coord);
    if (sdf < -innerBlurRadius - 1.0 || sdf > 1.0) return half4(0.0);
    float coverage = clamp(0.5 - sdf, 0.0, 1.0);
    float stroke = smoothstep(-strokeWidth, -strokeWidth + 1.0, sdf);
    half3 rgb = strokeColor.rgb * half(strokeAlphaMul * stroke * stroke);
    float depth = smoothstep(-innerBlurRadius, 0.0, sdf);
    float2 gradient = clearBloomGradient(coord);
    float gradientLength = length(gradient);
    if (gradientLength > 0.0001) {
        float2 normal = gradient / gradientLength * depth;
        float3 surfaceNormal = normalize(float3(normal, -sqrt(max(1.0 - dot(normal, normal), 0.0))));
        float3 litNormal = float3(surfaceNormal.x, -surfaceNormal.y, surfaceNormal.z);
        float light1 = dot(litNormal, lightDir1);
        float light2 = dot(litNormal, lightDir2);
        rgb += half(light1 * light1 * lightIntensity1) * strokeColor.rgb;
        rgb += half(light2 * light2 * lightIntensity2) * strokeColor.rgb;
    }
    float outputAlpha = clamp(max(max(float(rgb.r), float(rgb.g)), float(rgb.b)), 0.0, 1.0);
    return half4(rgb * half(highlightAlpha), half(outputAlpha * coverage * highlightAlpha));
}
"""

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
) {
    padding = maxOf(padding, 40.dp.toPx())
    noiseDither(0f)
    colorControls(brightness = preset.brightness, saturation = preset.saturation)
    blur(material.blurRadius.toPx())
    blendColors(colors)
    lens(preset.innerLensHeight.dp.toPx(), preset.innerLensAmount.dp.toPx())
    clearFace()
}

/** Glasmio Clear 的 max-luma face 与 extended-gamma sRGB 参数映射。 */
internal fun BackdropEffectScope.clearFace() {
    runtimeShaderEffect("OhMyFlyClearFace", ClearFaceShader, "child") {
        setFloatUniform("face", ClearFaceSaturation, ClearBlackPoint, ClearWhitePoint, 0f)
        setFloatUniform("maxLuma", ClearMaximumLuma)
    }
}

private val ClearFaceShader = """
uniform shader child;
uniform float4 face; // saturation, black point, white point, white fill alpha
uniform float maxLuma;

float encodeExtendedSrgb(float value) {
    float magnitude = abs(value);
    float encoded = magnitude <= 0.0031308
        ? 12.92 * magnitude
        : 1.055 * pow(magnitude, 1.0 / 2.4) - 0.055;
    return sign(value) * encoded;
}
float decodeExtendedSrgb(float value) {
    float magnitude = abs(value);
    float decoded = magnitude <= 0.04045
        ? magnitude / 12.92
        : pow((magnitude + 0.055) / 1.055, 2.4);
    return sign(value) * decoded;
}
float3 toNativeGamma(float3 working) {
    float3 linear = toLinearSrgb(working);
    return float3(encodeExtendedSrgb(linear.r), encodeExtendedSrgb(linear.g), encodeExtendedSrgb(linear.b));
}
float3 fromNativeGamma(float3 native) {
    float3 linear = float3(decodeExtendedSrgb(native.r), decodeExtendedSrgb(native.g), decodeExtendedSrgb(native.b));
    return fromLinearSrgb(linear);
}

half4 main(float2 coord) {
    half4 source = child.eval(coord);
    float alpha = float(source.a);
    if (alpha <= 0.000001) return source;
    float3 color = toNativeGamma(float3(source.rgb) / alpha);
    float luminance = dot(color, float3(0.2126, 0.7152, 0.0722));
    float gain = clamp(1.0 - luminance * (1.0 - maxLuma), 0.0, 1.0);
    color = mix(float3(gain * luminance), color * gain, 1.0 + 0.3 * (1.0 - gain));
    luminance = dot(color, float3(0.2126, 0.7152, 0.0722));
    color = (luminance * (face.z - face.y) + face.y) + (color - luminance) * face.x;
    color = mix(color, float3(1.0), face.w);
    float3 result = fromNativeGamma(color);
    return half4(half3(result * alpha), half(alpha));
}
"""

}

/** 背景模糊和色层由材质设置驱动，G2 轮廓共用于裁剪与边缘光。 */
internal fun Modifier.glassSurface(
    backdrop: Backdrop,
    shape: GlassShape,
    preset: GlassPreset,
    material: GlassMaterial,
    colors: BlurColors,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    onDrawFront: DrawScope.() -> Unit = {},
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = { glassEffects(preset, material, colors) },
    layerBlock = layerBlock,
    onDrawFront = onDrawFront,
)
