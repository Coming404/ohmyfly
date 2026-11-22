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
    fun defaultSettingsMapToClearMaterial() {
        val clear = sampleMaterial().withSettings(GlassMaterialSettings())
        assertEquals(0.75.dp, clear.blurRadius)
        assertEquals(Color.Transparent, clear.first.color)
        assertEquals(0f, clear.second!!.color.alpha, 0f)
        assertEquals(0f, clear.third!!.color.alpha, 0f)
    }

    @Test
    fun settingsApplyActualAlphaAndDpValues() {
        val material = sampleMaterial()
        val tuned = material.withSettings(
            GlassMaterialSettings(secondLayerAlpha = 0.34f, thirdLayerAlpha = 0.18f, blurRadiusDp = 8f),
        )
        assertEquals(8.dp, tuned.blurRadius)
        assertEquals(Color.Transparent, tuned.first.color)
        assertEquals(GlassColorBlendMode.SrcOver, tuned.first.mode)
        // Compose sRGB 将 alpha 量化到 8 位；测试允许一个通道步长。
        assertEquals(0.34f, tuned.second!!.color.alpha, 1f / 255f)
        assertEquals(0.18f, tuned.third!!.color.alpha, 1f / 255f)
    }

    private fun sampleMaterial() = GlassMaterial(
        blurRadius = 20.dp,
        first = GlassColorLayer(Color.Black.copy(alpha = 0.05f), GlassColorBlendMode.PlusDarker),
        second = GlassColorLayer(Color.White.copy(alpha = 0.6f), GlassColorBlendMode.SoftLight),
        third = GlassColorLayer(Color.White.copy(alpha = 0.4f), GlassColorBlendMode.HardLight),
    )
}
