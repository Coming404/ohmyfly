package com.ohmyfly.ui.glass

import com.ohmyfly.settings.GlassLensProfile
import com.ohmyfly.settings.MaterialMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassPresetTest {
    @Test
    fun lensProfilesKeepGoldenRatioAndScaleByComponentSize() {
        assertEquals(0.618f, LiquidGlassParameters.goldenRefractionScale)
        assertEquals(10f, GlassLensProfile.Pill.bezelWidthDp)
        assertEquals(38f, GlassLensProfile.Pill.thicknessDp)
        assertEquals(16f, GlassLensProfile.Control.bezelWidthDp)
        assertEquals(62f, GlassLensProfile.Control.thicknessDp)
        assertEquals(26f, GlassLensProfile.Card.bezelWidthDp)
        assertEquals(100f, GlassLensProfile.Card.thicknessDp)
    }

    @Test
    fun liquidUsesCentralizedClearParameters() {
        for (dark in listOf(false, true)) {
            val preset = glassPreset(MaterialMode.LiquidGlass, dark)
            assertEquals(LiquidGlassParameters.blurRadiusDp, preset.blur)
            assertEquals(LiquidGlassParameters.saturation, preset.saturation)
            assertEquals(LiquidGlassParameters.brightness, preset.brightness)
            assertEquals(LiquidGlassParameters.fillAlpha, preset.surfaceAlpha)
            assertEquals(LiquidGlassParameters.lensHeightDp, preset.lensHeight)
            assertEquals(LiquidGlassParameters.lensAmountDp, preset.lensAmount)
            assertEquals(LiquidGlassParameters.highlightAlpha, preset.highlightAlpha)
            assertEquals(LiquidGlassParameters.highlightBlurDp, preset.highlightBlur)
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
