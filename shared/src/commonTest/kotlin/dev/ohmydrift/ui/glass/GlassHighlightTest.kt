package dev.ohmydrift.ui.glass

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class GlassHighlightTest {
    @Test
    fun flatAndThresholdGravityUseTheStableLight() {
        val fallback = (-PI / 2).toFloat()
        assertEquals(fallback, quantizedGravityAngle(Offset.Zero))
        assertEquals(fallback, quantizedGravityAngle(Offset(0.099f, 0f)))
        assertEquals(fallback, quantizedGravityAngle(Offset(0.1f, 0f)))
    }

    @Test
    fun cardinalDirectionsRetainTheSensorMathCoordinateConvention() {
        assertEquals(0f, quantizedGravityAngle(Offset(1f, 0f)), 0.00001f)
        assertEquals((PI / 2).toFloat(), quantizedGravityAngle(Offset(0f, 1f)), 0.00001f)
        assertEquals(PI.toFloat(), quantizedGravityAngle(Offset(-1f, 0f)), 0.00001f)
        assertEquals((-PI / 2).toFloat(), quantizedGravityAngle(Offset(0f, -1f)), 0.00001f)
    }

    @Test
    fun threeDegreeQuantizationSuppressesSubstepChanges() {
        fun tilt(degrees: Float): Offset {
            val radians = degrees * PI.toFloat() / 180f
            return Offset(cos(radians), sin(radians))
        }
        assertEquals(0f, quantizedGravityAngle(tilt(1f)), 0.00001f)
        assertEquals((3 * PI / 180).toFloat(), quantizedGravityAngle(tilt(2f)), 0.00001f)
        assertEquals((-3 * PI / 180).toFloat(), quantizedGravityAngle(tilt(-2f)), 0.00001f)
    }

    @Test
    fun smallPlanarProjectionIsNotNormalizedIntoAnUnstableDirection() {
        assertEquals((-PI / 2).toFloat(), quantizedGravityAngle(Offset(0.01f, 0.01f)))
        assertEquals(0f, quantizedGravityAngle(Offset(0.8f, 0f)))
    }
}
