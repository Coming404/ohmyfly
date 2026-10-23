package com.ohmyfly.ui.glass

import com.ohmyfly.settings.MaterialMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassPresetAnimationTest {
    @Test
    fun interpolationKeepsBothEndpointsForEveryPresetPair() {
        for (dark in listOf(false, true)) for (from in MaterialMode.entries) for (to in MaterialMode.entries) {
            val start = glassPreset(from, dark)
            val end = glassPreset(to, dark)
            assertEquals(start.copy(liquidResponse = end.liquidResponse), interpolateGlassPreset(start, end, 0f))
            val actual = interpolateGlassPreset(start, end, 1f)
            assertEquals(end.blur, actual.blur, 0.00001f)
            assertEquals(end.saturation, actual.saturation, 0.00001f)
            assertEquals(end.brightness, actual.brightness, 0.00001f)
            assertEquals(end.surfaceAlpha, actual.surfaceAlpha, 0.00001f)
            assertEquals(end.lensHeight, actual.lensHeight, 0.00001f)
            assertEquals(end.lensAmount, actual.lensAmount, 0.00001f)
            assertEquals(end.highlightAlpha, actual.highlightAlpha, 0.00001f)
            assertEquals(end.highlightBlur, actual.highlightBlur, 0.00001f)
        }
    }

    @Test
    fun midpointIsBoundedAndInteractionUsesTargetPolicyImmediately() {
        val liquid = glassPreset(MaterialMode.LiquidGlass, false)
        val soft = glassPreset(MaterialMode.SoftGlass, false)
        val middle = interpolateGlassPreset(liquid, soft, 0.5f)
        assertEquals((liquid.blur + soft.blur) / 2f, middle.blur)
        assertEquals((liquid.lensAmount + soft.lensAmount) / 2f, middle.lensAmount)
        assertFalse(middle.liquidResponse)
        assertTrue(interpolateGlassPreset(soft, liquid, 0f).liquidResponse)
    }

    @Test
    fun fractionsOutsideRangeCannotOvershootMaterialBounds() {
        val start = glassPreset(MaterialMode.LiquidGlass, false)
        val end = glassPreset(MaterialMode.GaussianBlur, true)
        assertEquals(interpolateGlassPreset(start, end, 0f), interpolateGlassPreset(start, end, -10f))
        assertEquals(interpolateGlassPreset(start, end, 1f), interpolateGlassPreset(start, end, 10f))
    }
}
