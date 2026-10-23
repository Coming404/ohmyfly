package com.ohmyfly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import com.ohmyfly.settings.MaterialMode
import com.ohmyfly.ui.glass.GlassShape
import com.ohmyfly.ui.glass.InteractiveHighlight
import com.ohmyfly.ui.glass.rememberAnimatedGlassPreset
import com.ohmyfly.ui.glass.glassSurface
import com.ohmyfly.ui.glass.rememberGlassHighlight
import com.ohmyfly.ui.LocalAppMotionAllowed
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val ChromeCornerRadius = 50.dp
private val ChromeShape = GlassShape(ChromeCornerRadius)

/** The same preset/effect policy as the navigation shell, on actual back/action surfaces. */
@Composable
internal fun Modifier.chromeMaterial(
    backdrop: Backdrop,
    enabled: Boolean,
    dark: Boolean,
    materialMode: MaterialMode,
    shape: GlassShape = ChromeShape,
): Modifier {
    val surface = MiuixTheme.colorScheme.surface
    val motion = LocalAppMotionAllowed.current
    val preset = rememberAnimatedGlassPreset(materialMode, dark, enabled, motion)
    val highlight = rememberGlassHighlight(enabled, motion, preset)
    val spot = if (enabled && preset.liquidResponse && motion) {
        val scope = rememberCoroutineScope()
        remember(scope) { InteractiveHighlight(scope) }
    } else null
    val base = this
        .dropShadow(shape = shape, shadow = Shadow(radius = 8.dp, color = Color.Black, alpha = 0.12f))
        .clip(shape)
    val material = if (enabled) {
        base.glassSurface(
            backdrop, shape, preset, surface, highlight,
            pressProgress = { spot?.pressProgress ?: 0f },
        ).then(spot?.modifier ?: Modifier).then(spot?.trackingGestureModifier ?: Modifier)
    } else {
        base.background(surface)
    }
    return material.border(0.5.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f), shape)
}
