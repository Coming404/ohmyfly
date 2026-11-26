// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.ohmyfly.ui.glass

// Sampling and capsule adapted from Melox LiquidGlassNavigationBar.kt, 49ee06a.
// See third-party/Melox-NOTICE.md. No second navigation state or platform host is imported.
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ohmyfly.settings.MaterialMode
import com.ohmyfly.ui.AppDestination
import com.ohmyfly.ui.LocalAppMotionAllowed
import com.ohmyfly.ui.LocalChromeAppearance
import com.ohmyfly.ui.withSettings
import com.ohmyfly.ui.NavigationBarShape
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GlassNavigationSurface(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
    backdrop: Backdrop,
    enabled: Boolean,
    dark: Boolean,
    materialMode: MaterialMode,
    modifier: Modifier = Modifier,
) {
    var focusedIndex by remember { mutableIntStateOf(-1) }
    val motion = LocalAppMotionAllowed.current
    val preset = rememberAnimatedGlassPreset(materialMode, dark, enabled, motion)
        .withSettings(LocalChromeAppearance.current.materialSettings, dark)
    val response = rememberGlassNavigationGesture(
        selectedIndex = destination.ordinal,
        count = AppDestination.entries.size,
        effectsEnabled = enabled,
        liquid = preset.liquidResponse,
        motionAllowed = motion,
        onSelect = { onDestination(AppDestination.entries[it]) },
    )
    val highlight = rememberGlassHighlight(enabled, motion, preset)
    val navigationPreset = preset.copy(convexLensEnabled = false)
    val surface = MiuixTheme.colorScheme.surface
    val tint = glassTint(surface, dark)
    val spot = if (enabled && preset.liquidResponse && motion) response.spot else null
    val tabsBackdrop = rememberLayerBackdrop()
    Box(
        modifier = modifier
            .onSizeChanged { response.width = it.width.toFloat() }
            .graphicsLayer { translationX = response.panelOffset }
            .dropShadow(NavigationBarShape, Shadow(radius = 10.dp, color = Color.Black, alpha = if (dark) 0.2f else 0.1f))
            .then(response.drag.modifier)
            .then(spot?.trackingGestureModifier ?: Modifier),
    ) {
        Box(
            Modifier.matchParentSize().then(
                if (enabled) Modifier.glassSurface(
                    backdrop, NavigationBarShape, navigationPreset, tint, highlight,
                    pressProgress = { response.drag.pressProgress },
                    layerBlock = {
                        val scale = 1f + 16.dp.toPx() / size.width.coerceAtLeast(1f) * response.drag.pressProgress
                        scaleX = scale; scaleY = scale
                    },
                ) else Modifier.background(surface, NavigationBarShape),
            ).clip(NavigationBarShape).then(spot?.modifier ?: Modifier),
        )
        // Only this row measures the bar. heightIn in the tabs grows naturally with font scale.
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassNavigationTabs(
                destination.ordinal, { onDestination(AppDestination.entries[it]) },
                onFocusChange = { index, focused ->
                    if (focused) focusedIndex = index else if (focusedIndex == index) focusedIndex = -1
                },
            )
        }
        if (enabled) {
            // Alpha belongs OUTSIDE layerBackdrop: record real accent glyphs, but never draw the
            // duplicate directly. It has no focus, pointer or accessibility actions.
            Row(
                modifier = Modifier.matchParentSize().clearAndSetSemantics {}
                    .graphicsLayer { alpha = 0f }
                    .layerBackdrop(tabsBackdrop)
                    .glassSurface(backdrop, NavigationBarShape, navigationPreset, tint, highlight,
                        pressProgress = { response.drag.pressProgress })
                    .clip(NavigationBarShape).then(spot?.modifier ?: Modifier)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassNavigationTabs(destination.ordinal, {}, recording = true) {
                    1f + 0.2f * response.drag.pressProgress
                }
            }
        }
        GlassNavigationSelection(response, focusedIndex, dark)
    }
}

@Composable
private fun BoxScope.GlassNavigationSelection(
    response: GlassNavigationGesture,
    focusedIndex: Int,
    dark: Boolean,
) {
    if (response.tabWidth <= 0f) return
    val tabWidth = with(LocalDensity.current) { response.tabWidth.toDp() }
    val indicatorInset = if (top.yukonga.miuix.kmp.utils.platform() == top.yukonga.miuix.kmp.utils.Platform.IOS) {
        com.ohmyfly.ui.ChromeMetrics.NavigationIndicatorInset
    } else 4.dp
    Box(Modifier.matchParentSize().padding(horizontal = 4.dp, vertical = indicatorInset).clearAndSetSemantics {}) {
        Box(Modifier.fillMaxHeight().width(tabWidth).graphicsLayer {
            translationX = glassTabOffset(response.drag.value, response.tabWidth, response.ltr)
        }.background(
            MiuixTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.18f else 0.08f),
            NavigationBarShape,
        ))
    }
    if (focusedIndex in AppDestination.entries.indices) {
        val focusShape = com.ohmyfly.shapes.AppCorners.shape(12.dp)
        Box(Modifier.matchParentSize().padding(4.dp).clearAndSetSemantics {}) {
            Box(Modifier.fillMaxHeight().width(tabWidth).graphicsLayer {
                translationX = glassTabOffset(focusedIndex.toFloat(), response.tabWidth, response.ltr)
            }.border(2.dp, MiuixTheme.colorScheme.onSurface, focusShape))
        }
    }
}