package dev.ohmydrift.ui.glass

import dev.ohmydrift.settings.MaterialMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassPresetTest {
    @Test
    fun liquidBaseRetainsMeloxValuesInBothAppearances() {
        for (dark in listOf(false, true)) {
            val preset = glassPreset(MaterialMode.LiquidGlass, dark)
            assertEquals(4f, preset.blur)
            assertEquals(1.5f, preset.saturation)
            assertEquals(0.4f, preset.surfaceAlpha)
            assertEquals(24f, preset.lensHeight)
            assertEquals(24f, preset.lensAmount)
            assertEquals(0.75f, preset.highlightAlpha)
            assertEquals(2f, preset.highlightBlur)
            assertTrue(preset.liquidResponse)
        }
    }

    @Test
    fun allFourPresetsAreDistinctAndOnlyLiquidDeforms() {
        for (dark in listOf(false, true)) {
            val presets = MaterialMode.entries.map { glassPreset(it, dark) }
            assertEquals(4, presets.toSet().size)
            MaterialMode.entries.zip(presets).forEach { (mode, preset) ->
                assertEquals(mode == MaterialMode.LiquidGlass, preset.liquidResponse)
                assertTrue(preset.blur > 0f && preset.lensHeight > 0f && preset.lensAmount > 0f)
                assertTrue(preset.surfaceAlpha in 0f..1f && preset.highlightAlpha in 0f..1f)
            }
        }
    }

    @Test
    fun gaussianPreservesBaseBlurAndThemeOpacity() {
        val light = glassPreset(MaterialMode.GaussianBlur, false)
        val dark = glassPreset(MaterialMode.GaussianBlur, true)
        assertEquals(20f, light.blur)
        assertEquals(1f, light.saturation)
        assertEquals(0.22f, light.surfaceAlpha)
        assertEquals(0.28f, dark.surfaceAlpha)
        assertFalse(light.liquidResponse)
    }

    @Test
    fun softGlassIsMoreDiffuseAndOpaqueThanHighlightGlass() {
        for (dark in listOf(false, true)) {
            val soft = glassPreset(MaterialMode.SoftGlass, dark)
            val highlight = glassPreset(MaterialMode.HighlightGlass, dark)
            assertTrue(soft.blur > highlight.blur)
            assertTrue(soft.surfaceAlpha > highlight.surfaceAlpha)
            assertTrue(soft.highlightBlur > highlight.highlightBlur)
            assertTrue(soft.highlightAlpha < highlight.highlightAlpha)
        }
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
