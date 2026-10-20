package dev.ohmydrift.ui.glass

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ohmydrift.settings.MaterialMode
import dev.ohmydrift.ui.AppTransitionDurationMillis

/** Interpolate parameters in one effect chain; never crossfade recorded UI/shader trees. */
internal fun interpolateGlassPreset(start: GlassPreset, end: GlassPreset, fraction: Float): GlassPreset {
    val progress = fraction.coerceIn(0f, 1f)
    fun mix(a: Float, b: Float): Float = a + (b - a) * progress
    return GlassPreset(
        blur = mix(start.blur, end.blur),
        saturation = mix(start.saturation, end.saturation),
        brightness = mix(start.brightness, end.brightness),
        surfaceAlpha = mix(start.surfaceAlpha, end.surfaceAlpha),
        lensHeight = mix(start.lensHeight, end.lensHeight),
        lensAmount = mix(start.lensAmount, end.lensAmount),
        highlightAlpha = mix(start.highlightAlpha, end.highlightAlpha),
        highlightBlur = mix(start.highlightBlur, end.highlightBlur),
        // Interaction policy changes immediately, independently of cosmetic interpolation.
        liquidResponse = end.liquidResponse,
    )
}

@Composable
internal fun rememberAnimatedGlassPreset(
    mode: MaterialMode,
    dark: Boolean,
    enabled: Boolean,
    motionAllowed: Boolean,
): GlassPreset {
    val target = glassPreset(mode, dark)
    var current by remember { mutableStateOf(target) }
    LaunchedEffect(target, enabled, motionAllowed) {
        val start = current
        if (!enabled || !motionAllowed || start == target) {
            current = target
        } else {
            animate(0f, 1f, animationSpec = tween(AppTransitionDurationMillis)) { progress, _ ->
                current = interpolateGlassPreset(start, target, progress)
            }
            current = target
        }
    }
    // No transitional frame when accessibility/fallback policy becomes stricter.
    return if (!enabled || !motionAllowed) target else current.copy(liquidResponse = target.liquidResponse)
}
