package com.ohmyfly.ui.glass.sensor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

/**
 * Unit gravity projected onto the current screen: +x right, +y up (not Compose's y-down).
 * Magnitude is retained, not normalized to a unit 2D direction; near-flat (|g_xy| <= 0.1),
 * disabled and unavailable return [Offset.Zero]. The caller owns visibility/reduced-motion
 * gating through [enabled], and should read State in the draw phase, not composition.
 */
@Composable
internal expect fun rememberGlassGravity(enabled: Boolean): State<Offset>

internal const val GlassGravitySmoothing = 0.15f

/** Matches Miuix 0.9.4's device-space low-pass, including the first sample after restart. */
internal fun smoothGlassGravity(previous: Offset?, sample: Offset): Offset {
    if (!sample.x.isFinite() || !sample.y.isFinite()) return Offset.Zero
    if (previous == null) return sample
    return previous + (sample - previous) * GlassGravitySmoothing
}

/**
 * Quarter turns use Display.rotation's axis convention: 1 maps (x,y) to (-y,x).
 * iOS interface portrait/upside-down/landscapeRight/landscapeLeft maps to 0/2/1/3.
 */
internal fun screenGlassGravity(device: Offset, quarterTurns: Int): Offset {
    val (x, y) = device
    if (!x.isFinite() || !y.isFinite()) return Offset.Zero
    val magnitudeSquared = x * x + y * y
    if (!magnitudeSquared.isFinite() || magnitudeSquared <= 0.1f * 0.1f) return Offset.Zero
    // Clamp numerical overshoot without changing the gravity direction.
    val unit = if (magnitudeSquared > 1f) device / sqrt(magnitudeSquared) else device
    return when (quarterTurns) {
        0 -> unit
        1 -> Offset(-unit.y, unit.x)
        2 -> -unit
        3 -> Offset(unit.y, -unit.x)
        else -> Offset.Zero
    }
}
