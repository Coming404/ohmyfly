// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package dev.ohmydrift.ui.glass

// Adapted from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect

/**
 * Smooth-cubic refraction lens (ohmydrift boundary adaptation) with optional chromatic dispersion.
 *
 * @param chromaticAberration Strength of the rim chromatic dispersion. `0` disables the
 *  effect (cheaper non-dispersion shader is used). Typical values: `0.1` for subtle,
 *  `0.2` for Apple-pill-like, `0.3+` for pronounced rainbow halo. The dispersion offset
 *  scales with the refraction depth so it concentrates at the rim band's outer edge.
 */
internal fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f,
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding < refractionAmount) {
        padding = refractionAmount
    }

    val boundary = shape as? GlassShape ?: error("Glass Lens requires the shared GlassShape boundary")

    val dispersionEnabled = chromaticAberration > 0f
    val shaderString =
        if (dispersionEnabled) {
            ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER
        } else {
            ROUNDED_RECT_REFRACTION_SHADER
        }
    val key = if (dispersionEnabled) "LiquidGlassLensDispersion" else "LiquidGlassLens"

    val sf = downscaleFactor.coerceAtLeast(1).toFloat()
    val scaledSizeW = size.width / sf
    val scaledSizeH = size.height / sf
    val scaledPadding = padding / sf
    val scaledRefractionHeight = refractionHeight / sf
    val scaledRefractionAmount = refractionAmount / sf
    val scaledTile = boundary.tile(size, this) / sf

    runtimeShaderEffect(
        key = key,
        shaderString = shaderString,
        uniformShaderName = "content",
    ) {
        setFloatUniform("size", scaledSizeW, scaledSizeH)
        setFloatUniform("offset", -scaledPadding, -scaledPadding)
        setFloatUniform("cornerTile", scaledTile)
        setFloatUniform("refractionHeight", scaledRefractionHeight)
        setFloatUniform("refractionAmount", -scaledRefractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (dispersionEnabled) {
            setFloatUniform("chromaticAberration", chromaticAberration)
        }
    }
}

private const val ROUNDED_RECT_REFRACTION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float cornerTile;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

$GLASS_BOUNDARY_SDF

float circleMap(float x) {
    return 1.0 - sqrt(max(1.0 - x * x, 0.0));
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float3 boundary = glassBoundary(centeredCoord, halfSize, cornerTile);
    float sd = boundary.z;
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float2 centerDir = centeredCoord / max(length(centeredCoord), 0.001);
    float2 normal = boundary.xy + depthEffect * centerDir;
    float2 grad = normal / max(length(normal), 0.001);

    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""

private const val ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float cornerTile;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$GLASS_BOUNDARY_SDF

float circleMap(float x) {
    return 1.0 - sqrt(max(1.0 - x * x, 0.0));
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float3 boundary = glassBoundary(centeredCoord, halfSize, cornerTile);
    float sd = boundary.z;
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float2 centerDir = centeredCoord / max(length(centeredCoord), 0.001);
    float2 normal = boundary.xy + depthEffect * centerDir;
    float2 grad = normal / max(length(normal), 0.001);

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""
