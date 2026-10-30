package com.ohmyfly.ui.glass

import kotlin.test.Test
import kotlin.test.assertEquals

class GlassNavigationGeometryTest {
    @Test
    fun fiveTabsReserveExactlyFourDpOnBothEnds() {
        assertEquals(110.4f, glassTabWidth(560f, 4f, 5), 0.0001f)
        assertEquals(62.4f, glassTabWidth(320f, 4f, 5), 0.0001f)
    }

    @Test
    fun rightToLeftHitTestingMirrorsPhysicalCoordinates() {
        val width = 560f
        val cell = glassTabWidth(width, 4f, 5)
        repeat(5) { index ->
            val x = 4f + (index + 0.5f) * cell
            assertEquals(index, glassTabAt(x, width, 4f, 5, ltr = true))
            assertEquals(index, glassTabAt(width - x, width, 4f, 5, ltr = false))
        }
    }

    @Test
    fun insetsAndOverscrollClampToTheNearestLogicalTab() {
        assertEquals(0, glassTabAt(-20f, 560f, 4f, 5, true))
        assertEquals(4, glassTabAt(580f, 560f, 4f, 5, true))
        assertEquals(4, glassTabAt(-20f, 560f, 4f, 5, false))
        assertEquals(0, glassTabAt(580f, 560f, 4f, 5, false))
    }

    @Test
    fun preLayoutOrNarrowBarsHaveStableZeroWidth() {
        assertEquals(0f, glassTabWidth(0f, 4f, 5))
        assertEquals(0f, glassTabWidth(7f, 4f, 5))
        assertEquals(0f, glassTabWidth(560f, 4f, 0))
        assertEquals(0, glassTabAt(40f, 0f, 4f, 5, true))
        assertEquals(0, glassTabAt(40f, 560f, 4f, 0, false))
    }

    @Test
    fun capsuleOffsetsStayInLogicalOrderInBothDirections() {
        assertEquals(0f, glassTabOffset(0f, 62.4f, true))
        assertEquals(249.6f, glassTabOffset(4f, 62.4f, true), 0.0001f)
        assertEquals(-249.6f, glassTabOffset(4f, 62.4f, false), 0.0001f)
    }

    @Test
    fun fractionalDragPreservesSubpixelCapsulePosition() {
        assertEquals(156f, glassTabOffset(2.5f, 62.4f, true), 0.0001f)
        assertEquals(-156f, glassTabOffset(2.5f, 62.4f, false), 0.0001f)
    }
}
