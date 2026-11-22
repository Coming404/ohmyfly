package com.ohmyfly.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ohmyfly.settings.GlassMaterialSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import top.yukonga.miuix.kmp.material.GlassColorBlendMode
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassMaterial

class GlassMaterialTuningTest {
    @Test
    fun defaultSettingsPreserveEachComponentMaterial() {
        val material = sampleMaterial()
        assertEquals(material, material.withSettings(GlassMaterialSettings()))
    }

    @Test
    fun settingsApplyActualAlphaAndDpValues() {
        val material = sampleMaterial()
        val tuned = material.withSettings(
            GlassMaterialSettings(secondLayerAlpha = 0.34f, thirdLayerAlpha = 0.18f, blurRadiusDp = 8f),
        )
        assertEquals(8.dp, tuned.blurRadius)
        assertEquals(material.first, tuned.first)
        assertEquals(0.34f, tuned.second!!.color.alpha, 0.0001f)
        assertEquals(0.18f, tuned.third!!.color.alpha, 0.0001f)
    }

    private fun sampleMaterial() = GlassMaterial(
        blurRadius = 20.dp,
        first = GlassColorLayer(Color.Black.copy(alpha = 0.05f), GlassColorBlendMode.PlusDarker),
        second = GlassColorLayer(Color.White.copy(alpha = 0.6f), GlassColorBlendMode.SoftLight),
        third = GlassColorLayer(Color.White.copy(alpha = 0.4f), GlassColorBlendMode.HardLight),
    )
}
