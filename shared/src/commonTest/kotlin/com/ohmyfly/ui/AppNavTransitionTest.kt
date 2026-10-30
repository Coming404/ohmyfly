package com.ohmyfly.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.getVelocityFromNanos
import androidx.compose.animation.core.spring
import androidx.compose.runtime.MonotonicFrameClock
import com.ohmyfly.ui.settings.SettingsPage
import com.ohmyfly.ui.settings.popSettingsPage
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppNavTransitionTest {
    @Test
    fun everySettlePhaseUsesTheSubpageSpring() {
        val motion = AppNavTransition.motion
        assertSame(motion.programmatic, motion.commit)
        assertSame(motion.commit, motion.cancel)
        val spec = motion.programmatic as NavSettleSpec.Spring
        assertTrue(spec.dampingRatio in 0f..0.99f)
        assertTrue(!spec.clampOvershoot)
    }

    @Test
    fun translationIsContinuousAndDoesNotClampOrQuarterTheRebound() {
        for (depth in listOf(-1.04f, -1f, -0.02f, 0f, 0.02f, 1f, 1.04f)) {
            assertEquals(-depth * 360f, appNavTranslationX(depth, 360, false), 0.0001f)
        }
        val beforeRest = appNavTranslationX(-0.02f, 360, false)
        val afterRest = appNavTranslationX(0.02f, 360, false)
        assertEquals(-beforeRest, afterRest, 0.0001f)
    }

    @Test
    fun rtlMirrorsTheWholeTrackAndZeroWidthDoesNotMove() {
        for (depth in listOf(-3f, -1f, -0.04f, 0f, 0.04f, 1f, 3f)) {
            assertEquals(-appNavTranslationX(depth, 1080, false), appNavTranslationX(depth, 1080, true))
            assertEquals(0f, abs(appNavTranslationX(depth, 0, false)))
            assertEquals(0f, abs(appNavTranslationX(depth, 0, true)))
        }
    }

    @Test
    fun deepLayersStayJoinedDuringPushMultiPopAndOvershoot() {
        for (count in listOf(2, 3, 8, 32)) {
            for (top in listOf(-0.04f, 0f, 0.5f, count - 1f, count - 0.96f)) {
                for (rtl in listOf(false, true)) {
                    val positions = (0 until count).map { index -> appNavTranslationX(top - index, 360, rtl) }
                    positions.zipWithNext().forEach { (lower, upper) ->
                        assertEquals(if (rtl) -360f else 360f, upper - lower, 0.002f)
                    }
                }
            }
        }
    }

    @Test
    fun syntheticDeepSettingsStackPopsToAnIntactRoot() {
        val stack = navBackStackOf(*SettingsPage.entries.toTypedArray())
        while (stack.size > 1) {
            val oldTop = stack.lastIndex
            stack.popSettingsPage()
            val animation = trajectory(oldTop.toFloat(), stack.lastIndex.toFloat())
            for (millis in 0..700 step 10) {
                val top = animation.getValueFromNanos(millis * 1_000_000L)
                val depth = top - stack.lastIndex
                assertEquals(-depth * 360f, appNavTranslationX(depth, 360, false), 0.0001f)
            }
            assertEquals(0f, appNavTranslationX(animation.targetValue - stack.lastIndex, 360, false), 0.0001f)
        }
        stack.popSettingsPage()
        assertEquals(listOf(SettingsPage.Home), stack.toList())
    }

    @Test
    fun programmaticPushAndBackHaveVisibleButModestReboundAndSettle() {
        val push = trajectory(0f, 1f)
        val pop = trajectory(1f, 0f)
        val pushSamples = (0..1000).map { push.getValueFromNanos(it * 1_000_000L) }
        val popSamples = (0..1000).map { pop.getValueFromNanos(it * 1_000_000L) }
        val overshoot = pushSamples.max() - 1f
        assertTrue(overshoot in 0.025f..0.05f)
        assertTrue(pushSamples.indexOf(pushSamples.max()) in 180..280)
        assertEquals(overshoot, -popSamples.min(), 0.0001f)
        assertTrue(appNavTranslationX(overshoot, 360, false) < -9f)
        assertEquals(1f, pushSamples.last())
        assertEquals(0f, popSamples.last())
        assertTrue(push.durationNanos < 800_000_000L)
        assertTrue(pop.durationNanos < 800_000_000L)
    }

    @Test
    fun gestureReleaseKeepsItsPositionAndVelocity() {
        val commit = trajectory(0.64f, 0f, -1.8f, AppNavTransition.motion.commit)
        assertEquals(0.64f, commit.getValueFromNanos(0L))
        assertEquals(-1.8f, commit.getVelocityFromNanos(0L), 0.0001f)
        assertTrue(commit.getValueFromNanos(10_000_000L) < 0.64f)
        assertTrue((0..700).minOf { commit.getValueFromNanos(it * 1_000_000L) } < -0.01f)
        assertEquals(0f, commit.getValueFromNanos(1_000_000_000L))
    }

    @Test
    fun cancelSpringHonoursTheRuntimeRestBoundAndEndsAtIdentity() = runTest {
        withContext(StepFrameClock()) {
            val animation = Animatable(0.6f)
            // Miuix 0.9.4 settleCancel pins the depth at rest even for an underdamped spring.
            animation.updateBounds(upperBound = 1f)
            try {
                val result = animation.animateTo(
                    targetValue = 1f,
                    animationSpec = settleSpring(AppNavTransition.motion.cancel),
                    initialVelocity = 0.8f,
                ) { assertTrue(value <= 1f) }
                assertEquals(AnimationEndReason.BoundReached, result.endReason)
                assertEquals(1f, animation.value)
                assertEquals(0f, appNavTranslationX(animation.value - 1f, 360, false), 0.0001f)
            } finally {
                animation.updateBounds(lowerBound = null, upperBound = null)
            }
        }
    }

    private fun settleSpring(spec: NavSettleSpec): SpringSpec<Float> {
        val settle = spec as NavSettleSpec.Spring
        return spring(
            dampingRatio = settle.dampingRatio,
            stiffness = settle.stiffness,
            visibilityThreshold = 0.0025f,
        )
    }

    private fun trajectory(
        from: Float,
        to: Float,
        velocity: Float = 0f,
        spec: NavSettleSpec = AppNavTransition.motion.programmatic,
    ): TargetBasedAnimation<Float, AnimationVector1D> = TargetBasedAnimation(
        animationSpec = settleSpring(spec),
        typeConverter = Float.VectorConverter,
        initialValue = from,
        targetValue = to,
        initialVelocity = velocity,
    )

    private class StepFrameClock : MonotonicFrameClock {
        private var nanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            nanos += 16_666_667L
            return onFrame(nanos)
        }
    }
}
