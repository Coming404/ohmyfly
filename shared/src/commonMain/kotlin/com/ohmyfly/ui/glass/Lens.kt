// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.ohmyfly.ui.glass

// Lens motion adapted from Kyant0/AndroidLiquidGlass; G2 distance fields come from Miuix.
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.agsl.MaterialAgsl
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.material.setSilhouetteUniforms

/** 使用当前 G2 轮廓折射背景，并在按压时加入色散。 */
internal fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f,
) {
    if (!isRuntimeShaderSupported() || refractionHeight <= 0f || refractionAmount <= 0f) return
    val glassShape = shape as? GlassShape ?: error("G2 lens requires the shared Miuix GlassShape")
    val samplingPadding = padding
    val scale = downscaleFactor.coerceAtLeast(1f)
    padding = maxOf(padding, refractionAmount)
    runtimeShaderEffect(
        key = "DriftG2LiquidLens",
        shaderString = G2_REFRACTION_SHADER,
        uniformShaderName = "content",
    ) {
        setSilhouetteUniforms(glassShape, layoutDirection, size, this@lens, scale)
        setFloatUniform("offset", -samplingPadding / scale, -samplingPadding / scale)
        setFloatUniform("refractionHeight", refractionHeight / scale)
        setFloatUniform("refractionAmount", -refractionAmount / scale)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        setFloatUniform("chromaticAberration", chromaticAberration)
    }
}

private val G2_REFRACTION_SHADER = """
uniform shader content;
uniform float2 offset;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

${MaterialAgsl.SilhouetteSdf}

float2 lensGradient(float2 p) {
    float2 gradient = float2(
        silhouetteSdf(p + float2(0.5, 0.0)) - silhouetteSdf(p - float2(0.5, 0.0)),
        silhouetteSdf(p + float2(0.0, 0.5)) - silhouetteSdf(p - float2(0.0, 0.5))
    );
    return gradient / max(length(gradient), 0.001);
}

float lensCircleMap(float x) {
    return 1.0 - sqrt(max(1.0 - x * x, 0.0));
}

half4 main(float2 coord) {
    float2 local = coord + offset;
    float sd = silhouetteSdf(local);
    if (sd > 0.0) return content.eval(coord);
    if (-sd >= refractionHeight) return content.eval(coord);
    sd = min(sd, 0.0);
    float depth = clamp(-sd / refractionHeight, 0.0, 1.0);
    float distance = lensCircleMap(1.0 - depth) * refractionAmount;
    float2 centered = local - in_size * 0.5;
    float2 centerDirection = centered / max(length(centered), 0.001);
    float2 normal = lensGradient(local) + depthEffect * centerDirection;
    float2 direction = normal / max(length(normal), 0.001);
    float2 refracted = coord + distance * direction;
    if (chromaticAberration <= 0.0) return content.eval(refracted);

    float intensity = chromaticAberration * centered.x * centered.y /
        max(in_size.x * in_size.y * 0.25, 0.001);
    float2 dispersion = distance * direction * intensity;
    half4 color = half4(0.0);
    half4 red = content.eval(refracted + dispersion);
    color.r += red.r / 3.5; color.a += red.a / 7.0;
    half4 orange = content.eval(refracted + dispersion * (2.0 / 3.0));
    color.r += orange.r / 3.5; color.g += orange.g / 7.0; color.a += orange.a / 7.0;
    half4 yellow = content.eval(refracted + dispersion / 3.0);
    color.r += yellow.r / 3.5; color.g += yellow.g / 3.5; color.a += yellow.a / 7.0;
    half4 green = content.eval(refracted);
    color.g += green.g / 3.5; color.a += green.a / 7.0;
    half4 cyan = content.eval(refracted - dispersion / 3.0);
    color.g += cyan.g / 3.5; color.b += cyan.b / 3.0; color.a += cyan.a / 7.0;
    half4 blue = content.eval(refracted - dispersion * (2.0 / 3.0));
    color.b += blue.b / 3.0; color.a += blue.a / 7.0;
    half4 purple = content.eval(refracted - dispersion);
    color.r += purple.r / 7.0; color.b += purple.b / 3.0; color.a += purple.a / 7.0;
    return color;
}
"""
