// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package dev.ohmydrift.ui.glass

// Adapted from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.TimeSource

internal class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    private val motionAllowed: () -> Boolean,
    private val deformationAllowed: () -> Boolean,
    val canDrag: (Offset) -> Boolean = { true },
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDragCancelled: DampedDragAnimation.() -> Unit = onDragStopped,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {

    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()

    private var pressJob: Job? = null
    private var releaseJob: Job? = null

    private val velocityTracker = VelocityTracker()

    private val startMark = TimeSource.Monotonic.markNow()

    private fun nowMillis(): Long = startMark.elapsedNow().inWholeMilliseconds

    private var requestedValue = initialValue

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = requestedValue
    val pressProgress: Float get() = if (deformationAllowed() && motionAllowed()) pressProgressAnimation.value else 0f
    val scaleX: Float get() = if (deformationAllowed() && motionAllowed()) scaleXAnimation.value else initialScale
    val scaleY: Float get() = if (deformationAllowed() && motionAllowed()) scaleYAnimation.value else initialScale
    val velocity: Float get() = if (deformationAllowed() && motionAllowed()) velocityAnimation.value else 0f

    val modifier: Modifier = Modifier.pointerInput(this) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragCancelled()
                release()
            },
        ) { change, dragAmount ->
            val isInside = canDrag(change.position)
            val wasInside = canDrag(change.previousPosition)
            if (isInside && wasInside) {
                onDrag(size, dragAmount)
            }
        }
    }

    fun press() {
        if (!motionAllowed() || !deformationAllowed()) return
        releaseJob?.cancel()
        pressJob?.cancel()
        velocityTracker.resetTracking()
        pressJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        releaseJob?.cancel()
        if (!motionAllowed() || !deformationAllowed()) {
            pressJob?.cancel()
            releaseJob = animationScope.launch {
                pressProgressAnimation.snapTo(0f)
                scaleXAnimation.snapTo(initialScale)
                scaleYAnimation.snapTo(initialScale)
                velocityAnimation.snapTo(0f)
            }
            return
        }
        releaseJob = animationScope.launch {
            withFrameMillis { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }.first { abs(it - valueAnimation.targetValue) < threshold }
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        requestedValue = targetValue
        animationScope.launch {
            if (motionAllowed()) {
                valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() }
            } else {
                valueAnimation.snapTo(targetValue)
            }
        }
    }

    suspend fun snapToValue(value: Float) {
        requestedValue = value.coerceIn(valueRange)
        pressJob?.cancel()
        releaseJob?.cancel()
        valueAnimation.snapTo(value.coerceIn(valueRange))
        velocityAnimation.snapTo(0f)
        pressProgressAnimation.snapTo(0f)
        scaleXAnimation.snapTo(initialScale)
        scaleYAnimation.snapTo(initialScale)
    }

    fun animateToValue(value: Float) {
        requestedValue = value.coerceIn(valueRange)
        animationScope.launch {
            mutatorMutex.mutate {
                if (!motionAllowed()) {
                    snapToValue(value)
                    return@mutate
                }
                press()
                val targetValue = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(nowMillis(), Offset(value, 0f))
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
        val targetVelocity = velocityTracker.calculateVelocity().x / span
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            velocityAnimation.snapTo(targetVelocity)
        }
    }
}

internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(false)
        var pointer = down.id
        var distance = Offset.Zero
        var dragging = false
        var finished = false
        onDragStart(down)
        onDrag(down, Offset.Zero)
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointer } ?: break
                if (!change.pressed) {
                    val replacement = event.changes.firstOrNull { it.pressed }
                    if (replacement != null) {
                        pointer = replacement.id
                        continue
                    }
                    // Let selectable own taps, keyboard and accessibility activation. A drag
                    // consumes movement after slop, so it cannot also activate the release tab.
                    if (dragging) {
                        change.consume()
                        onDragEnd(change)
                        finished = true
                    }
                    break
                }
                val delta = change.positionChange()
                if (change.isConsumed) break
                if (!dragging) {
                    distance += delta
                    if (abs(distance.x) <= viewConfiguration.touchSlop) continue
                    dragging = true
                    onDrag(change, distance)
                } else {
                    onDrag(change, delta)
                }
                change.consume()
            }
        } finally {
            if (!finished) onDragCancel()
        }
    }
}
