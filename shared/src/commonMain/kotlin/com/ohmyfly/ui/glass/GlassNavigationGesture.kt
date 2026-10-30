// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.ohmyfly.ui.glass

// Adapted from Melox LiquidGlassNavigationBar.kt, 49ee06a; see third-party/Melox-NOTICE.md.
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.launch

/** Only a visual/gesture preview. The App's destination is the sole committed selection. */
internal class GlassNavigationGesture(
    val drag: DampedDragAnimation,
    val spot: InteractiveHighlight,
    val ltr: Boolean,
    private val inset: Float,
    private val count: Int,
    measuredWidth: MutableFloatState,
    private val panelTranslation: () -> Float,
) {
    var width by measuredWidth
    val tabWidth: Float get() = glassTabWidth(width, inset, count)
    val panelOffset: Float get() = panelTranslation()
}

@Composable
internal fun rememberGlassNavigationGesture(
    selectedIndex: Int,
    count: Int,
    effectsEnabled: Boolean,
    liquid: Boolean,
    motionAllowed: Boolean,
    onSelect: (Int) -> Unit,
): GlassNavigationGesture {
    val density = LocalDensity.current
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val inset = with(density) { 4.dp.toPx() }
    val measuredWidth = remember { mutableFloatStateOf(0f) }
    // Replacing only the gesture owner cancels old springs/spot jobs, not pages or selection.
    return key(motionAllowed, effectsEnabled, liquid, count, inset, ltr) {
        val scope = rememberCoroutineScope()
        val selected by rememberUpdatedState(selectedIndex)
        val select by rememberUpdatedState(onSelect)
        val moving = motionAllowed
        val deforming = effectsEnabled && liquid && motionAllowed
        val response = remember(scope, count, inset, ltr) {
            val offset = Animatable(0f)
            lateinit var response: GlassNavigationGesture
            fun returnPanel() {
                scope.launch {
                    if (deforming) offset.animateTo(0f, spring(1f, 300f, 0.5f))
                    else offset.snapTo(0f)
                }
            }
            val drag = DampedDragAnimation(
                animationScope = scope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(count - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                motionAllowed = { moving },
                deformationAllowed = { deforming },
                canDrag = { it.x in 0f..response.width },
                onDragStarted = { position ->
                    updateValue(glassTabAt(position.x, response.width, inset, count, ltr).toFloat())
                },
                onDragStopped = {
                    val index = targetValue.roundToInt().coerceIn(0, count - 1)
                    if (selected != index) select(index)
                    updateValue(index.toFloat())
                    returnPanel()
                },
                onDragCancelled = {
                    updateValue(selected.toFloat())
                    returnPanel()
                },
                onDrag = { _, delta ->
                    if (response.tabWidth > 0f && delta.x != 0f) {
                        updateValue(targetValue + delta.x / response.tabWidth * if (ltr) 1f else -1f)
                        if (deforming) scope.launch { offset.snapTo(offset.value + delta.x) }
                    }
                },
            )
            val spot = InteractiveHighlight(
                animationScope = scope,
                position = { size, _ ->
                    val center = inset + (drag.value + 0.5f) * response.tabWidth
                    Offset(if (ltr) center else size.width - center, size.height / 2f)
                },
            )
            response = GlassNavigationGesture(drag, spot, ltr, inset, count, measuredWidth) {
                if (!deforming || response.width <= 0f) 0f else {
                    val fraction = (offset.value / response.width).coerceIn(-1f, 1f)
                    inset * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
            response
        }
        LaunchedEffect(selectedIndex, response, motionAllowed, effectsEnabled, liquid) {
            if (!motionAllowed || !effectsEnabled) {
                response.drag.snapToValue(selectedIndex.toFloat())
            } else if (response.drag.targetValue != selectedIndex.toFloat()) {
                // Initial composition and mode switches are not synthetic presses.
                response.drag.animateToValue(selectedIndex.toFloat())
            }
        }
        response
    }
}
