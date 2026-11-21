package com.ohmyfly.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import com.ohmyfly.settings.AccentPalette
import com.ohmyfly.settings.MaterialMode
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class AppThemeTest {
    @Test
    fun dynamicSeedUsesNeutral2025AndRequestedAppearance() {
        val seed = Color(0xFF52856C)
        for (dark in listOf(false, true)) {
            val controller = appThemeController(seed, dark)
            assertEquals(ColorSchemeMode.MonetSystem, controller.colorSchemeMode)
            assertEquals(seed, controller.keyColor)
            assertEquals(ThemePaletteStyle.Neutral, controller.paletteStyle)
            assertEquals(ThemeColorSpec.Spec2025, controller.colorSpec)
            assertEquals(dark, controller.isDark)
        }
    }

    @Test
    fun absentSeedKeepsBuiltInSchemesAndRequestedAppearance() {
        for (dark in listOf(false, true)) {
            val controller = appThemeController(null, dark)
            assertEquals(ColorSchemeMode.System, controller.colorSchemeMode)
            assertNull(controller.keyColor)
            assertEquals(dark, controller.isDark)
            assertEquals(lightColorScheme().primary, controller.lightColors.primary)
            assertEquals(lightColorScheme().surface, controller.lightColors.surface)
            assertEquals(darkColorScheme().primary, controller.darkColors.primary)
            assertEquals(darkColorScheme().surface, controller.darkColors.surface)
        }
    }

    @Test
    fun automaticPaletteUsesAndroidSeedAndIosExpressiveDefault() {
        val systemSeed = Color(0xFF526A8C)
        assertEquals(systemSeed, appThemeController(systemSeed, false).keyColor)
        val expressive = appThemeController(null, false, MaterialMode.Expressive)
        assertEquals(Color(0xFF1F6FE5), expressive.keyColor)
        assertEquals(ThemePaletteStyle.Expressive, expressive.paletteStyle)
    }

    @Test
    fun explicitPresetOverridesSystemSeedAndKeepsMaterialPaletteRecipe() {
        val preset = AccentPalette.Miyazaki
        for (mode in MaterialMode.entries) {
            val controller = appThemeController(Color(0xFF112233), false, mode, preset)
            assertEquals(Color(preset.seedArgb!!), controller.keyColor)
            assertEquals(
                if (mode == MaterialMode.Expressive) ThemePaletteStyle.Expressive else ThemePaletteStyle.Neutral,
                controller.paletteStyle,
            )
        }
    }

    @Test
    fun changingSystemColorIsNotReplacedWithFixedBrandSeed() {
        val first = Color(0xFFA56D4E)
        val second = Color(0xFF49785D)
        assertEquals(first, appThemeController(first, false).keyColor)
        assertEquals(second, appThemeController(second, false).keyColor)
        assertEquals(second, appThemeController(second, true).keyColor)
    }
}
