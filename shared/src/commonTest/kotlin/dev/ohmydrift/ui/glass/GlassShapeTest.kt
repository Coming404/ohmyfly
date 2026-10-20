package dev.ohmydrift.ui.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.ohmydrift.ui.NavigationBarShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GlassShapeTest {
    @Test
    fun cubicCornerHasTheSameAxisEndpointsAsTheClip() {
        assertEquals(Offset(1f, 0f), glassCornerPoint(0f))
        assertEquals(Offset(0f, 1f), glassCornerPoint(1f))
    }

    @Test
    fun cubicCornerIsMonotoneAndStaysInsideItsTile() {
        var previous = glassCornerPoint(0f)
        for (step in 1..100) {
            val point = glassCornerPoint(step / 100f)
            assertTrue(point.x in 0f..1.000001f && point.y in 0f..1.000001f)
            assertTrue(point.x <= previous.x + 0.000001f && point.y >= previous.y - 0.000001f)
            previous = point
        }
    }

    @Test
    fun cubicUsesTheMiuixControlRatherThanACircularProxy() {
        val midpoint = glassCornerPoint(0.5f)
        assertEquals(0.741125f, midpoint.x, 0.000001f)
        assertEquals(midpoint.x, midpoint.y, 0.000001f)
    }

    @Test
    fun cubicIsSymmetricUnderAxisExchange() {
        for (step in 0..20) {
            val forward = glassCornerPoint(step / 20f)
            val reverse = glassCornerPoint(1f - step / 20f)
            assertEquals(forward.x, reverse.y, 0.000001f)
            assertEquals(forward.y, reverse.x, 0.000001f)
        }
    }

    @Test
    fun shaderTileUsesTheClipExtensionAndClampsAtHalfTheShortEdge() {
        val shape = GlassShape(24.dp)
        assertEquals(26.4f, shape.tile(Size(100f, 64f), Density(1f)), 0.0001f)
        assertEquals(52.8f, shape.tile(Size(200f, 128f), Density(2f)), 0.0001f)
        assertEquals(10f, shape.tile(Size(100f, 20f), Density(1f)))
    }

    @Test
    fun navigationRetainsGenericSmoothOutlineInBothDirections() {
        val density = Density(1f)
        val size = Size(320f, 64f)
        val ltr = assertIs<Outline.Generic>(NavigationBarShape.createOutline(size, LayoutDirection.Ltr, density))
        val rtl = assertIs<Outline.Generic>(NavigationBarShape.createOutline(size, LayoutDirection.Rtl, density))
        assertEquals(ltr.path.getBounds(), rtl.path.getBounds())
        assertEquals(24.dp, NavigationBarShape.radius)
    }
}
