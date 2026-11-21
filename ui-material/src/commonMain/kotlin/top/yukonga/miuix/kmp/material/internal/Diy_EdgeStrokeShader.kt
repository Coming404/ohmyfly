// 官方边缘光照使用共享 G2 距离场法线。
@file:kotlin.jvm.JvmName("EdgeStrokeShaderKt")
package top.yukonga.miuix.kmp.material.internal

import top.yukonga.miuix.kmp.material.EdgePeak
import top.yukonga.miuix.kmp.material.agsl.MaterialAgsl

/** Compiled-shader key and AGSL source for one [EdgePeak]. */
internal class EdgeStrokeShader(val key: String, val source: String)

// Source assembly interpolates large AGSL fragments; build each peak once instead of per draw.
private val edgeStrokeShaders: Map<EdgePeak, Lazy<EdgeStrokeShader>> = EdgePeak.entries.associateWith { peak ->
    lazy { EdgeStrokeShader("MiuixEdgeStroke" + peak.name, buildEdgeStrokeShader(peak)) }
}

/** Cached key and source for [peak]. */
internal fun edgeStrokeShader(peak: EdgePeak): EdgeStrokeShader = edgeStrokeShaders.getValue(peak).value

/** Bevel normal along the edge stroke, as AGSL source. */
internal fun edgeStrokeNormalSource(): String = """
float3 rimNormal(float2 coord, float2 half2d, float sdf, float radius, float inner) {
    // 使用同一距离场梯度，避免高光仍按普通圆弧估计法线。
    float2 v=float2(silhouetteSdf(coord+float2(0.5,0.0))-silhouetteSdf(coord-float2(0.5,0.0)),
        silhouetteSdf(coord+float2(0.0,0.5))-silhouetteSdf(coord-float2(0.0,0.5)));
    float depth=clamp(-sdf/max(inner,0.0001),0.0,1.0);
    float radial=1.0-depth;
    float z=-sqrt(max(1.0-radial*radial,0.0));
    return length(v)>0.0001 ? float3(normalize(v)*radial,z) : float3(0.0,0.0,-1.0);
}
"""

/** Directional lights of the edge stroke, as AGSL source. */
internal fun edgeStrokeLightsSource(peak: EdgePeak): String {
    val lightBlock = when (peak) {
        EdgePeak.Vertical ->
            """
    float lit1 = clamp(dot(normal, in_light1.xyz) * (-normal.y), 0.0, 1.0);
    rgb += in_light1Color.rgb * (lit1 * lit1 * in_light1.w);
    float lit2 = clamp(dot(normal, in_light2.xyz) * normal.y, 0.0, 1.0);
    rgb += in_light2Color.rgb * (lit2 * lit2 * in_light2.w);
"""

        EdgePeak.Axial ->
            """
    float gate1 = max(dot(float3(axis1, 0.0), normal), 0.0);
    float lit1 = clamp(dot(normal, in_light1.xyz) * gate1, 0.0, 1.0);
    rgb += in_light1Color.rgb * (lit1 * lit1 * in_light1.w);
    float gate2 = max(dot(float3(axis2, 0.0), normal), 0.0);
    float lit2 = clamp(dot(normal, in_light2.xyz) * gate2, 0.0, 1.0);
    rgb += in_light2Color.rgb * (lit2 * lit2 * in_light2.w);
"""

        EdgePeak.Dual ->
            """
    float peak1 = dot(normal.xy, in_light1.xy);
    rgb += in_light1Color.rgb * (peak1 * peak1 * in_light1.w);
    float peak2 = dot(normal.xy, in_light2.xy);
    rgb += in_light2Color.rgb * (peak2 * peak2 * in_light2.w);
"""
    }
    return """
float3 edgeStrokeLights(float3 normal) {
    float3 rgb = float3(0.0);
$lightBlock
    return rgb;
}
"""
}

/**
 * Builds the edge-stroke shader: a flat stroke band hugging the silhouette plus a hemispheric rim
 * normal lit by two directional lights. The silhouette comes from [MaterialAgsl.SilhouetteSdf], so
 * `in_smoothing = 0` compiles the superellipse branch away. [EdgePeak] picks the gate.
 *
 * Light directions are pre-normalized on the CPU side and `in_light*.w` carries the intensity; under
 * [EdgePeak.Axial] the gate is signed and the *product* is clamped, not either dot separately.
 *
 * Output is premultiplied: the alpha carries the light's own magnitude, so additive channels are
 * never clamped away by a lower alpha.
 */
internal fun buildEdgeStrokeShader(peak: EdgePeak): String {
    val axisUniforms = if (peak == EdgePeak.Axial) {
        """
uniform float2 axis1;
uniform float2 axis2;
"""
    } else {
        ""
    }
    return """
${MaterialAgsl.SilhouetteSdf}
uniform float2 in_halfViewFloor; // half size, floored, so the fold lands on a pixel boundary
uniform float2 in_strokeBand;    // stroke width, bevel
uniform float4 in_strokeColor;   // stroke rgb, stroke opacity
uniform float in_strokeAlpha;    // surface opacity
uniform float4 in_light1;        // direction xyz, intensity
uniform float4 in_light1Color;   // colour rgb, unused
uniform float4 in_light2;        // direction xyz, intensity
uniform float4 in_light2Color;   // colour rgb, unused
$axisUniforms
${edgeStrokeNormalSource()}
${edgeStrokeLightsSource(peak)}
half4 main(float2 coord) {
    float2 half2d = in_size * 0.5;
    float2 folded = abs(coord - half2d);
    float radius = pickRadius(coord, half2d);
    float inner = max(in_strokeBand.y, 0.5);
    float bevel = max(radius, inner);

    // The interior has no rim to light, and it is most of the surface.
    if (folded.x < half2d.x - bevel && folded.y < half2d.y - bevel) return half4(0.0);

    float sdf = silhouetteSdf(coord);
    // A near-step band hugging the outer edge, squared by compositing it twice.
    float width = max(in_strokeBand.x, 0.75);
    float band = smoothstep(-width, -width + 1.0, sdf);
    float3 rgb = in_strokeColor.rgb * (in_strokeColor.a * band * band);
    float3 normal = rimNormal(coord, half2d, sdf, radius, inner);
    rgb += edgeStrokeLights(normal);
    // Light only: alpha stays at zero so a transparent surface never gains opacity from the rim.
    float coverage = clamp(0.5 - sdf, 0.0, 1.0);
    float3 light = rgb * (in_strokeAlpha * coverage);
    // Premultiplied, so the alpha has to match the light: one whose channels sit above its own
    // alpha is clamped away.
    return half4(half3(light), half(clamp(max(max(light.r, light.g), light.b), 0.0, 1.0)));
}
"""
}
