package com.ohmyfly.ui.glass

import com.ohmyfly.settings.GlassLensProfile
import com.ohmyfly.settings.MaterialMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassPresetTest {
    @Test
    fun upstreamProfilesMatchTheSharedLiquidGlassPresets() {
        assertEquals(0.618f, GlassLensProfile.Pill.refractionScale)
        for (profile in GlassLensProfile.entries) {
            assertEquals(6f, profile.recipeBlurDp)
            assertEquals(0.70f, profile.refractionStrength)
            assertEquals(0.50f, profile.depthFraction)
            assertEquals(0.22f, profile.dispersion)
        }
    }

    @Test
    fun liquidUsesCentralizedUpstreamParameters() {
        val light = glassPreset(MaterialMode.LiquidGlass, dark = false)
        val dark = glassPreset(MaterialMode.LiquidGlass, dark = true)
        assertEquals(2f, light.blur)
        assertEquals(1.5f, light.saturation)
        assertEquals(0f, light.brightness)
        assertEquals(0.4f, light.surfaceAlpha)
        assertEquals(0.4f, dark.surfaceAlpha)
        assertEquals(LiquidGlassParameters.highlightAlpha, light.highlightAlpha)
        assertEquals(LiquidGlassParameters.highlightBlurDp, light.highlightBlur)
        assertTrue(light.liquidResponse)
    }

    @Test
    fun expressiveUsesTheNonGlassFallbackPreset() {
        val preset = glassPreset(MaterialMode.Expressive, dark = false)
        assertFalse(preset.liquidResponse)
        assertEquals(0f, preset.surfaceAlpha)
        assertEquals(0f, preset.highlightAlpha)
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
