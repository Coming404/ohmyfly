package com.ohmyfly.ui.glass

import com.ohmyfly.settings.MaterialMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassPresetTest {
    @Test
    fun liquidKeepsItsOpticsWithClearerThemeOpacity() {
        for (dark in listOf(false, true)) {
            val preset = glassPreset(MaterialMode.LiquidGlass, dark)
            assertEquals(4f, preset.blur)
            assertEquals(1.5f, preset.saturation)
            assertEquals(if (dark) 0.10f else 0.30f, preset.surfaceAlpha)
            assertEquals(24f, preset.lensHeight)
            assertEquals(24f, preset.lensAmount)
            assertEquals(1f, preset.highlightAlpha)
            assertEquals(0.25f, preset.highlightBlur)
            assertTrue(preset.liquidResponse)
        }
    }

    @Test
    fun expressiveUsesTheNonGlassFallbackPreset() {
        val preset = glassPreset(MaterialMode.Expressive, dark = false)
        assertFalse(preset.liquidResponse)
        assertEquals(0f, preset.surfaceAlpha)
        assertEquals(0f, preset.highlightAlpha)
        assertEquals(0f, preset.lensHeight)
        assertEquals(0f, preset.lensAmount)
    }

    @Test
    fun onlyLiquidGlassUsesInteractiveRefraction() {
        assertTrue(glassPreset(MaterialMode.LiquidGlass, dark = false).liquidResponse)
        assertFalse(glassPreset(MaterialMode.Expressive, dark = false).liquidResponse)
    }

    @Test
    fun reducingTransparencyStopsEffectsEvenOnCapableDevices() {
        assertFalse(glassEffectsEnabled(reduceTransparency = true, shaderSupported = true, systemReduceTransparency = false))
        assertFalse(glassEffectsEnabled(reduceTransparency = true, shaderSupported = false, systemReduceTransparency = false))
    }

    @Test
    fun unsupportedShadersCannotLeaveTransparentUnsampledSurfaces() {
        assertFalse(glassEffectsEnabled(reduceTransparency = false, shaderSupported = false, systemReduceTransparency = false))
        assertTrue(glassEffectsEnabled(reduceTransparency = false, shaderSupported = true, systemReduceTransparency = false))
    }

    @Test
    fun eitherApplicationOrSystemReductionWinsOverShaderSupport() {
        for (application in listOf(false, true)) {
            for (system in listOf(false, true)) {
                for (supported in listOf(false, true)) {
                    assertEquals(
                        !application && !system && supported,
                        glassEffectsEnabled(application, supported, system),
                        "application=$application system=$system supported=$supported",
                    )
                }
            }
        }
    }
}
