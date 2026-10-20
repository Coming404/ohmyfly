package dev.ohmydrift.ui.glass.sensor

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassGravityTest {
    @Test
    fun portraitUsesRightAndUpDeviceAxes() {
        assertOffset(Offset(1f, 0f), screenGlassGravity(Offset(1f, 0f), 0))
        assertOffset(Offset(0f, 1f), screenGlassGravity(Offset(0f, 1f), 0))
    }

    @Test
    fun rotation90MapsToMinusYAndX() {
        assertOffset(Offset(0f, 1f), screenGlassGravity(Offset(1f, 0f), 1))
        assertOffset(Offset(-1f, 0f), screenGlassGravity(Offset(0f, 1f), 1))
    }

    @Test
    fun upsideDownReversesBothAxes() {
        assertOffset(Offset(-1f, 0f), screenGlassGravity(Offset(1f, 0f), 2))
        assertOffset(Offset(0f, -1f), screenGlassGravity(Offset(0f, 1f), 2))
    }

    @Test
    fun rotation270MapsToYAndMinusX() {
        assertOffset(Offset(0f, -1f), screenGlassGravity(Offset(1f, 0f), 3))
        assertOffset(Offset(1f, 0f), screenGlassGravity(Offset(0f, 1f), 3))
    }

    @Test
    fun portraitGravityDownStaysScreenDownInAllFourPhysicalOrientations() {
        // Android rotations 0/1/2/3 = iOS portrait/landscapeRight/upsideDown/landscapeLeft.
        val physicalGravity = listOf(Offset(0f, -1f), Offset(-1f, 0f), Offset(0f, 1f), Offset(1f, 0f))
        physicalGravity.forEachIndexed { turns, device ->
            assertOffset(Offset(0f, -1f), screenGlassGravity(device, turns))
        }
    }

    @Test
    fun projectionRetainsTiltMagnitudeInsteadOfNormalizingEveryDirection() {
        for (turns in 0..3) {
            val projected = screenGlassGravity(Offset(0.3f, 0.4f), turns)
            assertEquals(0.5f, sqrt(projected.x * projected.x + projected.y * projected.y), 0.00001f)
        }
    }

    @Test
    fun flatAndExactlyPointOneGravityAreZero() {
        for (turns in 0..3) {
            for (device in listOf(Offset.Zero, Offset(0.1f, 0f), Offset(0f, -0.1f), Offset(0.06f, 0.08f))) {
                assertEquals(Offset.Zero, screenGlassGravity(device, turns))
            }
        }
    }

    @Test
    fun justAboveFlatThresholdRetainsItsMagnitude() {
        assertOffset(Offset(0.1001f, 0f), screenGlassGravity(Offset(0.1001f, 0f), 0))
    }

    @Test
    fun subThresholdDiagonalIsZero() {
        assertEquals(Offset.Zero, screenGlassGravity(Offset(0.05f, -0.05f), 1))
    }

    @Test
    fun unitDiscOvershootIsClampedWithoutChangingDirection() {
        assertOffset(Offset(0.6f, 0.8f), screenGlassGravity(Offset(0.9f, 1.2f), 0))
        assertOffset(Offset(-0.8f, 0.6f), screenGlassGravity(Offset(0.9f, 1.2f), 1))
    }

    @Test
    fun unknownRotationUsesStaticZeroNotAnInventedPortraitDirection() {
        for (turns in listOf(-1, 4, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals(Offset.Zero, screenGlassGravity(Offset(0.3f, 0.4f), turns))
        }
    }

    @Test
    fun nonFiniteDeviceSamplesAreStaticZero() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(Offset.Zero, screenGlassGravity(Offset(invalid, 0.5f), 0))
            assertEquals(Offset.Zero, screenGlassGravity(Offset(0.5f, invalid), 1))
        }
    }

    @Test
    fun overflowInProjectionMagnitudeIsStaticZero() {
        assertEquals(Offset.Zero, screenGlassGravity(Offset(Float.MAX_VALUE, Float.MAX_VALUE), 0))
    }

    @Test
    fun firstSampleAndRestartAreNotAttenuated() {
        val sample = Offset(0.3f, -0.8f)
        assertEquals(sample, smoothGlassGravity(null, sample))
        smoothGlassGravity(sample, Offset(-0.3f, 0.8f))
        assertEquals(sample, smoothGlassGravity(null, sample))
    }

    @Test
    fun subsequentSamplesUseMiuixPointFifteenLowPassExactlyOnce() {
        assertOffset(Offset(0.15f, -0.15f), smoothGlassGravity(Offset.Zero, Offset(1f, -1f)))
        assertOffset(Offset(0.2775f, -0.2775f), smoothGlassGravity(Offset(0.15f, -0.15f), Offset(1f, -1f)))
    }

    @Test
    fun steadySampleDoesNotDrift() {
        val sample = Offset(-0.3f, 0.7f)
        assertEquals(sample, smoothGlassGravity(sample, sample))
    }

    @Test
    fun deviceSpaceFilteringPrecedesRotationWithoutCrossOrientationInterpolation() {
        val filtered = smoothGlassGravity(Offset(0.2f, 0.8f), Offset(0.4f, 0.6f))
        assertOffset(Offset(0.23f, 0.77f), screenGlassGravity(filtered, 0))
        assertOffset(Offset(-0.77f, 0.23f), screenGlassGravity(filtered, 1))
        assertOffset(Offset(0.77f, -0.23f), screenGlassGravity(filtered, 3))
    }

    @Test
    fun invalidSamplesDoNotPoisonTheLowPassOutput() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(Offset.Zero, smoothGlassGravity(Offset(0.5f, 0.5f), Offset(invalid, 0f)))
            assertEquals(Offset.Zero, smoothGlassGravity(null, Offset(0f, invalid)))
        }
    }

    @Test
    fun repeatedLowPassApproachesTargetWithoutOvershoot() {
        var filtered = Offset.Zero
        repeat(100) {
            val next = smoothGlassGravity(filtered, Offset(1f, -1f))
            assertTrue(next.x in filtered.x..1f)
            assertTrue(next.y in -1f..filtered.y)
            filtered = next
        }
        assertOffset(Offset(1f, -1f), filtered)
    }

    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.00001f)
        assertEquals(expected.y, actual.y, 0.00001f)
    }
}
