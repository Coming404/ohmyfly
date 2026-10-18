package dev.ohmydrift.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {
    @Test
    fun onlySystemModeFollowsSystemAppearance() {
        assertFalse(ThemeMode.System.isDark(false))
        assertTrue(ThemeMode.System.isDark(true))
        assertFalse(ThemeMode.Light.isDark(false))
        assertFalse(ThemeMode.Light.isDark(true))
        assertTrue(ThemeMode.Dark.isDark(false))
        assertTrue(ThemeMode.Dark.isDark(true))
    }
}
