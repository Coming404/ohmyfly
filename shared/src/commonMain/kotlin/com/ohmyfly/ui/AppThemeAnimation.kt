package com.ohmyfly.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import top.yukonga.miuix.kmp.theme.Colors

/** All 53 public color roles in Miuix 0.9.4; generation remains in ThemeController. */
internal fun interpolateThemeColors(start: Colors, end: Colors, fraction: Float): Colors {
    val progress = fraction.coerceIn(0f, 1f)
    fun mix(a: Color, b: Color): Color = lerp(a, b, progress)
    return Colors(
        primary = mix(start.primary, end.primary),
        onPrimary = mix(start.onPrimary, end.onPrimary),
        primaryVariant = mix(start.primaryVariant, end.primaryVariant),
        onPrimaryVariant = mix(start.onPrimaryVariant, end.onPrimaryVariant),
        error = mix(start.error, end.error),
        onError = mix(start.onError, end.onError),
        errorContainer = mix(start.errorContainer, end.errorContainer),
        onErrorContainer = mix(start.onErrorContainer, end.onErrorContainer),
        disabledPrimary = mix(start.disabledPrimary, end.disabledPrimary),
        disabledOnPrimary = mix(start.disabledOnPrimary, end.disabledOnPrimary),
        disabledPrimaryButton = mix(start.disabledPrimaryButton, end.disabledPrimaryButton),
        disabledOnPrimaryButton = mix(start.disabledOnPrimaryButton, end.disabledOnPrimaryButton),
        disabledPrimarySlider = mix(start.disabledPrimarySlider, end.disabledPrimarySlider),
        primaryContainer = mix(start.primaryContainer, end.primaryContainer),
        onPrimaryContainer = mix(start.onPrimaryContainer, end.onPrimaryContainer),
        secondary = mix(start.secondary, end.secondary),
        onSecondary = mix(start.onSecondary, end.onSecondary),
        secondaryVariant = mix(start.secondaryVariant, end.secondaryVariant),
        onSecondaryVariant = mix(start.onSecondaryVariant, end.onSecondaryVariant),
        disabledSecondary = mix(start.disabledSecondary, end.disabledSecondary),
        disabledOnSecondary = mix(start.disabledOnSecondary, end.disabledOnSecondary),
        disabledSecondaryVariant = mix(start.disabledSecondaryVariant, end.disabledSecondaryVariant),
        disabledOnSecondaryVariant = mix(start.disabledOnSecondaryVariant, end.disabledOnSecondaryVariant),
        secondaryContainer = mix(start.secondaryContainer, end.secondaryContainer),
        onSecondaryContainer = mix(start.onSecondaryContainer, end.onSecondaryContainer),
        secondaryContainerVariant = mix(start.secondaryContainerVariant, end.secondaryContainerVariant),
        onSecondaryContainerVariant = mix(start.onSecondaryContainerVariant, end.onSecondaryContainerVariant),
        tertiaryContainer = mix(start.tertiaryContainer, end.tertiaryContainer),
        onTertiaryContainer = mix(start.onTertiaryContainer, end.onTertiaryContainer),
        tertiaryContainerVariant = mix(start.tertiaryContainerVariant, end.tertiaryContainerVariant),
        background = mix(start.background, end.background),
        onBackground = mix(start.onBackground, end.onBackground),
        onBackgroundVariant = mix(start.onBackgroundVariant, end.onBackgroundVariant),
        surface = mix(start.surface, end.surface),
        onSurface = mix(start.onSurface, end.onSurface),
        surfaceVariant = mix(start.surfaceVariant, end.surfaceVariant),
        onSurfaceSecondary = mix(start.onSurfaceSecondary, end.onSurfaceSecondary),
        onSurfaceVariantSummary = mix(start.onSurfaceVariantSummary, end.onSurfaceVariantSummary),
        onSurfaceVariantActions = mix(start.onSurfaceVariantActions, end.onSurfaceVariantActions),
        disabledOnSurface = mix(start.disabledOnSurface, end.disabledOnSurface),
        surfaceContainer = mix(start.surfaceContainer, end.surfaceContainer),
        onSurfaceContainer = mix(start.onSurfaceContainer, end.onSurfaceContainer),
        onSurfaceContainerVariant = mix(start.onSurfaceContainerVariant, end.onSurfaceContainerVariant),
        surfaceContainerHigh = mix(start.surfaceContainerHigh, end.surfaceContainerHigh),
        onSurfaceContainerHigh = mix(start.onSurfaceContainerHigh, end.onSurfaceContainerHigh),
        surfaceContainerHighest = mix(start.surfaceContainerHighest, end.surfaceContainerHighest),
        onSurfaceContainerHighest = mix(start.onSurfaceContainerHighest, end.onSurfaceContainerHighest),
        outline = mix(start.outline, end.outline),
        dividerLine = mix(start.dividerLine, end.dividerLine),
        windowDimming = mix(start.windowDimming, end.windowDimming),
        sliderKeyPoint = mix(start.sliderKeyPoint, end.sliderKeyPoint),
        sliderKeyPointForeground = mix(start.sliderKeyPointForeground, end.sliderKeyPointForeground),
        sliderBackground = mix(start.sliderBackground, end.sliderBackground),
    )
}

@Composable
internal fun rememberAnimatedThemeColors(target: Colors, motionAllowed: Boolean): Colors {
    var current by remember { mutableStateOf(target) }
    LaunchedEffect(target, motionAllowed) {
        val start = current
        if (!motionAllowed || start === target) {
            current = target
        } else {
            animate(0f, 1f, animationSpec = tween(AppTransitionDurationMillis)) { progress, _ ->
                current = interpolateThemeColors(start, target, progress)
            }
            current = target
        }
    }
    return if (motionAllowed) current else target
}
