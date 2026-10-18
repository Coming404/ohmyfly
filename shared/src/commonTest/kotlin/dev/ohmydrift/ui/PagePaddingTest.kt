package dev.ohmydrift.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class PagePaddingTest {
    @Test
    fun addsSpacingWithoutLosingFloatingBarOrSystemInsets() {
        val result = PaddingValues(start = 4.dp, top = 72.dp, end = 8.dp, bottom = 88.dp)
            .withPageSpacing(LayoutDirection.Ltr, horizontal = 12.dp)
        assertEquals(16.dp, result.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(20.dp, result.calculateEndPadding(LayoutDirection.Ltr))
        assertEquals(84.dp, result.calculateTopPadding())
        assertEquals(100.dp, result.calculateBottomPadding())
    }

    @Test
    fun preservesLogicalEdgesInRtlAndDefaultsToNoHorizontalInset() {
        val result = PaddingValues(start = 4.dp, end = 8.dp)
            .withPageSpacing(LayoutDirection.Rtl)
        assertEquals(4.dp, result.calculateStartPadding(LayoutDirection.Rtl))
        assertEquals(8.dp, result.calculateEndPadding(LayoutDirection.Rtl))
        assertEquals(8.dp, result.calculateLeftPadding(LayoutDirection.Rtl))
        assertEquals(4.dp, result.calculateRightPadding(LayoutDirection.Rtl))
        assertEquals(12.dp, result.calculateTopPadding())
        assertEquals(12.dp, result.calculateBottomPadding())
    }
}
