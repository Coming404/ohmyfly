package com.ohmyfly.ui

import com.ohmyfly.settings.GlassMaterialSettings
import com.ohmyfly.ui.glass.GlassPreset
import kotlin.test.Test
import kotlin.test.assertEquals

class GlassMaterialTuningTest {
    @Test
    fun settingsOverrideGlassAndConvexProfileParameters() {
        val base = GlassPreset(4f, 1.5f, 0f, 0.3f, 1f, 0.25f, true)
        val tuned = base.withSettings(
            GlassMaterialSettings(
                secondLayerAlpha = 0.34f,
                thirdLayerAlpha = 0.18f,
                blurRadiusDp = 8f,
                convexLensEnabled = true,
            ), dark = false,
        )
        assertEquals(8f, tuned.blur)
        assertEquals(0.34f, tuned.surfaceAlpha)
        assertEquals(0.18f, tuned.highlightAlpha)
        assertEquals(true, tuned.convexLensEnabled)
    }

    @Test
    fun unsetValuesKeepOriginalPresetUntilConvexLensIsEnabled() {
        val base = GlassPreset(4f, 1.5f, 0f, 0.3f, 1f, 0.25f, true)
        assertEquals(base, base.withSettings(GlassMaterialSettings(), dark = false))
        val pill = base.withSettings(GlassMaterialSettings(convexLensEnabled = true), dark = false)
        assertEquals(0.2f, pill.blur)
        assertEquals(0.25f, pill.surfaceAlpha)
    }
}
