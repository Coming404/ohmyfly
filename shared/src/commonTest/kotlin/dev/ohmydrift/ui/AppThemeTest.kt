package dev.ohmydrift.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
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
    fun changingSystemColorIsNotReplacedWithFixedBrandSeed() {
        val first = Color(0xFFA56D4E)
        val second = Color(0xFF49785D)
        assertEquals(first, appThemeController(first, false).keyColor)
        assertEquals(second, appThemeController(second, false).keyColor)
        assertEquals(second, appThemeController(second, true).keyColor)
    }
}
