// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.ohmyfly.ui.glass

// Melox/Kyant0 light preset; Miuix BloomStroke adapted only for its smooth cubic boundary.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.ohmyfly.ui.glass.sensor.rememberGlassGravity
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.blur.RuntimeShader
import top.yukonga.miuix.kmp.blur.RuntimeShaderCache
import top.yukonga.miuix.kmp.blur.asComposeShader
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.HighlightStyle
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource

internal fun quantizedGravityAngle(gravity: Offset): Float {
    val step = (3.0 * PI / 180.0).toFloat()
    val threshold = 0.1f
    return if (gravity.getDistanceSquared() > threshold * threshold) {
        (atan2(gravity.y, gravity.x) / step).roundToInt() * step
    } else (-PI / 2).toFloat()
}

@Composable
internal fun rememberGlassHighlight(
    enabled: Boolean,
    motionAllowed: Boolean,
    preset: GlassPreset,
    extraDegrees: Float = -45f,
): State<Highlight> {
    val gravity = rememberGlassGravity(enabled && motionAllowed && preset.highlightAlpha > 0f)
    // Neither gravity nor the angle is read by composition/layout. Quantization suppresses draw invalidations.
    return remember(gravity, preset, extraDegrees) {
        derivedStateOf {
            val angle = quantizedGravityAngle(gravity.value) + (extraDegrees * PI / 180.0).toFloat()
            Highlight(
                width = 1.dp,
                alpha = preset.highlightAlpha,
                style = SmoothBloomStroke(BloomStroke(
                    color = Color.White.copy(alpha = 0.12f),
                    innerBlurRadius = preset.highlightBlur.dp,
                    // Sensor coordinates are y-up; shader/light positions use Compose y-down.
                    primaryLight = LightSource(LightPosition(0.5f + cos(angle), 0.7f - sin(angle), -0.05f), intensity = 1f),
                    secondaryLight = LightSource(LightPosition(0.5f, 0.8f, -0.5f), intensity = 0.4f),
                    dualPeak = true,
                )),
            )
        }
    }
}

/** Upstream BloomStroke lighting and public shader cache; no half-height fallback for Generic Outline. */
private data class SmoothBloomStroke(val bloom: BloomStroke) : HighlightStyle {
    override val color get() = bloom.color
    override val blendMode get() = bloom.blendMode

    override fun DrawScope.createShader(
        shape: Shape,
        strokeWidthPx: Float,
        highlightAlpha: Float,
        runtimeShaderCache: RuntimeShaderCache,
    ): Shader? {
        val boundary = shape as? GlassShape ?: error("BloomStroke requires the shared GlassShape boundary")
        val shader = runtimeShaderCache.obtainRuntimeShader("DriftSmoothBloomDual", SMOOTH_BLOOM_SHADER)
        shader.setFloatUniform("halfView", size.width * 0.5f, size.height * 0.5f)
        shader.setFloatUniform("cornerTile", boundary.tile(size, this))
        shader.setFloatUniform("strokeWidth", strokeWidthPx)
        shader.setFloatUniform("innerBlurRadius", bloom.innerBlurRadius.toPx())
        shader.setFloatUniform("highlightAlpha", highlightAlpha)
        shader.setColorUniform("strokeColor", bloom.color.copy(alpha = 1f))
        shader.setFloatUniform("strokeAlphaMul", bloom.color.alpha)
        shader.setLight("1", bloom.primaryLight)
        shader.setLight("2", bloom.secondaryLight)
        return shader.asComposeShader()
    }
}

private fun RuntimeShader.setLight(suffix: String, light: LightSource) {
    val x = light.position.x - 0.5f
    val y = light.position.y - 0.7f
    val z = light.position.z
    val length = sqrt(x*x + y*y + z*z).coerceAtLeast(1e-6f)
    setFloatUniform("lightDir$suffix", x/length, y/length, z/length)
    setColorUniform("lightColor$suffix", light.color.copy(alpha = 1f))
    setFloatUniform("lightIntensity$suffix", light.color.alpha * light.intensity)
}

private const val SMOOTH_BLOOM_SHADER = """
uniform float2 halfView;
uniform float cornerTile;
uniform float strokeWidth;
uniform float innerBlurRadius;
uniform float highlightAlpha;
layout(color) uniform half4 strokeColor;
uniform float strokeAlphaMul;
uniform float3 lightDir1;
layout(color) uniform half4 lightColor1;
uniform float lightIntensity1;
uniform float3 lightDir2;
layout(color) uniform half4 lightColor2;
uniform float lightIntensity2;
$GLASS_BOUNDARY_SDF
half4 main(float2 coord) {
    float3 edge = glassBoundary(coord-halfView, halfView, cornerTile);
    float sdf = edge.z;
    if (sdf < -innerBlurRadius-1.0 || sdf > 1.0) return half4(0.0);
    half outMask = half(smoothstep(0.0, -1.0, sdf));
    float strokeAlpha = smoothstep(-strokeWidth, -strokeWidth+1.0, sdf);
    half3 rgb = strokeColor.rgb * half(strokeAlphaMul * strokeAlpha * strokeAlpha);
    float t = smoothstep(-innerBlurRadius, 0.0, sdf);
    float3 n = normalize(float3(edge.xy * t, -sqrt(max(1.0-t*t,0.0))));
    float l1 = dot(n.xy, lightDir1.xy);
    rgb += half(l1*l1*lightIntensity1) * lightColor1.rgb;
    float l2 = dot(n.xy, lightDir2.xy);
    rgb += half(l2*l2*lightIntensity2) * lightColor2.rgb;
    return half4(rgb * half(highlightAlpha), 1.0) * outMask;
}
"""
