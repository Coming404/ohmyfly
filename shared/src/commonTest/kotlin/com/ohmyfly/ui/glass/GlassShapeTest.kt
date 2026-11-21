package com.ohmyfly.ui.glass

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ohmyfly.ui.NavigationBarShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GlassShapeTest {
    @Test
    fun contentCornerRadiiScaleByDensityAndClampToHalfBounds() {
        val shape = com.ohmyfly.shapes.AppCorners.shape(16.dp) as com.ohmyfly.shapes.ContinuousShape
        assertEquals(16.dp, shape.radius)
        val rounded = com.kyant.shapes.RoundedRectangle(shape.radius, com.kyant.shapes.RoundedCornerStyle.Continuous)
        val size = Size(200f, 160f)
        for ((density, expected) in listOf(Density(1f) to 16f, Density(2f) to 32f)) {
            val corners = rounded.corners(size, LayoutDirection.Ltr, density)
            assertEquals(expected, corners.topLeft)
            assertEquals(expected, corners.topRight)
            assertEquals(expected, corners.bottomRight)
            assertEquals(expected, corners.bottomLeft)
        }
        val constrained = com.kyant.shapes.RoundedRectangle(100.dp, com.kyant.shapes.RoundedCornerStyle.Continuous)
            .corners(size, LayoutDirection.Ltr, Density(2f))
        assertEquals(80f, constrained.topLeft)
        assertEquals(80f, constrained.topRight)
        assertEquals(80f, constrained.bottomRight)
        assertEquals(80f, constrained.bottomLeft)
    }

    @Test
    fun saturatedRadiusKeepsCircleAndCapsuleGeometry() {
        val size = Size(92f, 44f)
        val density = Density(1f)
        val expected = assertIs<Outline.Rounded>(CircleShape.createOutline(size, LayoutDirection.Ltr, density))
        val actual = assertIs<Outline.Rounded>(com.ohmyfly.shapes.AppCorners.shape(50.dp)
            .createOutline(size, LayoutDirection.Ltr, density))
        assertEquals(expected.roundRect, actual.roundRect)
    }

    @Test
    fun navigationBoundaryMatchesCircleAtEveryAspectRatioAndDensity() {
        val shape = assertIs<GlassShape>(NavigationBarShape)
        val sizes = listOf(Size(272f, 64f), Size(52.8f, 56f), Size(52.8f, 120f), Size(128f, 640f))
        for (size in sizes) for (density in listOf(Density(1f), Density(2f))) {
            for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                val expected = assertIs<Outline.Rounded>(CircleShape.createOutline(size, direction, density))
                val actual = assertIs<Outline.Rounded>(shape.createOutline(size, direction, density))
                assertEquals(expected.roundRect, actual.roundRect)
                assertEquals(-size.minDimension * 0.5f, shape.tile(size, density), 0.0001f)
            }
        }
    }
}
