package dev.ohmydrift.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class AppSurfaceColorsTest {
    @Test
    fun builtInLightSeparatesCanvasWithoutChangingCardColors() {
        val colors = lightColorScheme()
        val surfaces = appSurfaceColors(colors, dynamic = false, dark = false)
        assertEquals(Color(0xFFF0F1F3), surfaces.page)
        assertNotEquals(colors.surface, surfaces.page)
        assertEquals(colors.surfaceContainer, surfaces.card)
        assertEquals(colors.onSurfaceContainer, surfaces.onCard)
        assertEquals(Color.Black.copy(alpha = 0.04f), surfaces.cardOutline)
    }

    @Test
    fun dynamicLightMapsLibraryRolesTogetherWithTheirForeground() {
        val colors = lightColorScheme(
            surface = Color(0xFFF9F7F4),
            onSurface = Color(0xFF1B1C19),
            surfaceContainer = Color(0xFFF0EEEA),
            surfaceContainerHigh = Color(0xFFE8E6E2),
            onSurfaceContainer = Color(0xFF292A26),
        )
        val surfaces = appSurfaceColors(colors, dynamic = true, dark = false)
        assertEquals(colors.surfaceContainerHigh, surfaces.page)
        assertEquals(colors.surface, surfaces.card)
        assertEquals(colors.onSurface, surfaces.onCard)
        assertNotEquals(surfaces.page, surfaces.card)
        assertEquals(Color.Black.copy(alpha = 0.04f), surfaces.cardOutline)
    }

    @Test
    fun darkPreservesExistingCanvasAndContainerForBothColorSources() {
        val colors = darkColorScheme()
        for (dynamic in listOf(false, true)) {
            val surfaces = appSurfaceColors(colors, dynamic, dark = true)
            assertEquals(colors.surface, surfaces.page)
            assertEquals(colors.surfaceContainer, surfaces.card)
            assertEquals(colors.onSurfaceContainer, surfaces.onCard)
            assertEquals(Color.White.copy(alpha = 0.08f), surfaces.cardOutline)
        }
    }
}
