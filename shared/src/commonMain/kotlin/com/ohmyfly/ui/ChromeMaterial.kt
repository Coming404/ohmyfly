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
import com.ohmyfly.settings.GlassMaterialSettings
import com.ohmyfly.settings.MaterialMode
import com.ohmyfly.ui.glass.rememberGlassHighlight
import com.ohmyfly.ui.glass.GlassShape
import com.ohmyfly.ui.glass.InteractiveHighlight
import com.ohmyfly.ui.glass.rememberAnimatedGlassPreset
import com.ohmyfly.ui.glass.glassSurface
import com.ohmyfly.ui.glass.glassTint
import androidx.compose.ui.graphics.graphicsLayer
import com.ohmyfly.ui.LocalAppMotionAllowed
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val ChromeShape = NavigationBarShape

/** The same preset/effect policy as the navigation shell, on actual back/action surfaces. */
@Composable
internal fun Modifier.chromeMaterial(
    backdrop: Backdrop,
    enabled: Boolean,
    dark: Boolean,
    materialMode: MaterialMode,
    shape: GlassShape = ChromeShape,
    materialSettings: GlassMaterialSettings? = null,
): Modifier {
    val settings = materialSettings ?: LocalChromeAppearance.current.materialSettings
    val surface = MiuixTheme.colorScheme.surface
    val motion = LocalAppMotionAllowed.current
    val preset = rememberAnimatedGlassPreset(materialMode, dark, enabled, motion)
        .withSettings(settings)
    val outline = shape
    val highlight = rememberGlassHighlight(enabled, motion, preset)
    val spot = if (enabled && preset.liquidResponse && motion) {
        val scope = rememberCoroutineScope()
        remember(scope) { InteractiveHighlight(scope) }
    } else null
    val base = this.then(if (spot == null) Modifier else Modifier.graphicsLayer {
        val press = spot.pressProgress
        scaleX = 1f + 0.025f * press
        scaleY = 1f - 0.025f * press
    })
        .dropShadow(shape = outline, shadow = Shadow(radius = 8.dp, color = Color.Black, alpha = 0.12f))
        .clip(outline)
    val material = if (enabled) {
        base.glassSurface(
            backdrop, shape, preset, glassTint(surface, dark), highlight,
            pressProgress = { spot?.pressProgress ?: 0f },
        ).then(spot?.modifier ?: Modifier).then(spot?.trackingGestureModifier ?: Modifier)
    } else {
        base.background(surface)
    }
    return material.border(ChromeMetrics.OutlineWidth,
        MiuixTheme.colorScheme.onSurface.copy(alpha = ChromeMetrics.OutlineAlpha), outline)
}
