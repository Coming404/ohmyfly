// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// Miuix 0.9.4 cubic boundary, shared by the app's clip, refraction and lighting.
package com.ohmyfly.ui.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.squircle.addSquircleRect

/** Same cubic as Miuix 0.9.4 addSquircleRect (extension=1.1, control=0.643). */
internal open class GlassShape(val radius: Dp) : Shape {
    fun tile(size: Size, density: Density): Float =
        (with(density) { radius.toPx() } * 1.1f).coerceIn(0f, size.minDimension * 0.5f)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply {
            addSquircleRect(size.width, size.height, with(density) { radius.toPx() }, extension = 1.1f)
        })
}

/** Shared by Lens and BloomStroke: nearest point on that very cubic, not a rounded-rect proxy. */
internal const val GLASS_BOUNDARY_SDF = """
float2 cornerPoint(float t) {
    float u = 1.0 - t;
    return float2(u*u*u + 3.0*u*u*t + 3.0*0.643*u*t*t,
                  3.0*0.643*u*u*t + 3.0*u*t*t + t*t*t);
}
float2 cornerTangent(float t) {
    float u = 1.0 - t;
    return 3.0 * (u*u*float2(0.0,0.643) + 2.0*u*t*float2(-0.357,0.357) + t*t*float2(-0.643,0.0));
}
float2 cornerAcceleration(float t) {
    return 6.0 * ((1.0-t)*float2(-0.357,-0.286) + t*float2(-0.286,-0.357));
}
// Returns outward unit normal in xy and signed distance in z.
float3 glassBoundary(float2 p, float2 halfSize, float tile) {
    float2 a = abs(p);
    float2 edge = a - halfSize;
    float2 q = (a - (halfSize - tile)) / max(tile, 0.001);
    if (tile < 0.001 || q.x <= 0.0 || q.y <= 0.0) {
        float2 n = edge.x > edge.y ? float2(1.0,0.0) : float2(0.0,1.0);
        return float3(n * sign(p), max(edge.x, edge.y));
    }
    float t = clamp(0.5 + (q.y-q.x)*0.5, 0.0, 1.0);
    for (int i=0; i<6; i++) {
        float2 b = cornerPoint(t);
        float2 v = cornerTangent(t);
        float denom = dot(v,v) + dot(b-q,cornerAcceleration(t));
        t = clamp(t - dot(b-q,v)/max(denom,0.001), 0.0, 1.0);
    }
    float2 b = cornerPoint(t);
    float2 v = cornerTangent(t);
    float2 n = normalize(float2(v.y,-v.x));
    float sd = length(q-b) * tile * (dot(q-b,n) < 0.0 ? -1.0 : 1.0);
    return float3(n * sign(p), sd);
}
"""

/** CPU reference used by geometry tests. Coordinates are in the positive corner tile. */
internal fun glassCornerPoint(t: Float): Offset {
    val u = 1f - t
    return Offset(u*u*u + 3f*u*u*t + 3f*0.643f*u*t*t, 3f*0.643f*u*u*t + 3f*u*t*t + t*t*t)
}
