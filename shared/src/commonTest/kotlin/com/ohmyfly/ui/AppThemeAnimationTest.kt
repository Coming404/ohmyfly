package com.ohmyfly.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.test.Test
import kotlin.test.assertEquals
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class AppThemeAnimationTest {
    private fun roles(colors: Colors): List<Color> = with(colors) { listOf(
        primary, onPrimary, primaryVariant, onPrimaryVariant, error, onError, errorContainer, onErrorContainer,
        disabledPrimary, disabledOnPrimary, disabledPrimaryButton, disabledOnPrimaryButton, disabledPrimarySlider,
        primaryContainer, onPrimaryContainer, secondary, onSecondary, secondaryVariant, onSecondaryVariant,
        disabledSecondary, disabledOnSecondary, disabledSecondaryVariant, disabledOnSecondaryVariant,
        secondaryContainer, onSecondaryContainer, secondaryContainerVariant, onSecondaryContainerVariant,
        tertiaryContainer, onTertiaryContainer, tertiaryContainerVariant,
        background, onBackground, onBackgroundVariant, surface, onSurface, surfaceVariant,
        onSurfaceSecondary, onSurfaceVariantSummary, onSurfaceVariantActions, disabledOnSurface,
        surfaceContainer, onSurfaceContainer, onSurfaceContainerVariant, surfaceContainerHigh, onSurfaceContainerHigh,
        surfaceContainerHighest, onSurfaceContainerHighest, outline, dividerLine, windowDimming,
        sliderKeyPoint, sliderKeyPointForeground, sliderBackground,
    ) }

    @Test
    fun allRolesKeepEndpointsAndInterpolateWithoutOmissions() {
        val start = lightColorScheme()
        val end = darkColorScheme()
        assertEquals(53, roles(start).size)
        for (fraction in listOf(0f, 0.25f, 0.5f, 1f)) {
            val actual = roles(interpolateThemeColors(start, end, fraction))
            val expected = roles(start).zip(roles(end)) { a, b -> lerp(a, b, fraction) }
            assertEquals(expected, actual)
        }
    }

    @Test
    fun partialTransitionCanRetargetFromItsCurrentDisplayedColors() {
        val light = lightColorScheme()
        val dark = darkColorScheme()
        val displayed = interpolateThemeColors(light, dark, 0.4f)
        val returned = interpolateThemeColors(displayed, light, 0.5f)
        assertEquals(lerp(displayed.surface, light.surface, 0.5f), returned.surface)
        assertEquals(roles(displayed), roles(interpolateThemeColors(displayed, light, 0f)))
        assertEquals(roles(light), roles(interpolateThemeColors(displayed, light, 1f)))
    }

    @Test
    fun fractionsCannotOvershoot() {
        val start = lightColorScheme()
        val end = darkColorScheme()
        assertEquals(roles(interpolateThemeColors(start, end, 0f)), roles(interpolateThemeColors(start, end, -1f)))
        assertEquals(roles(interpolateThemeColors(start, end, 1f)), roles(interpolateThemeColors(start, end, 2f)))
    }

    @Test
    fun pageAndCardsUseTheirOwnMappedEndpointsDuringAppearanceChange() {
        for (dynamic in listOf(false, true)) {
            val start = appSurfaceColors(lightColorScheme(), dynamic, false)
            val end = appSurfaceColors(darkColorScheme(), dynamic, true)
            val actual = interpolateAppSurfaceColors(start, end, 0.5f)
            assertEquals(lerp(start.page, end.page, 0.5f), actual.page)
            assertEquals(lerp(start.card, end.card, 0.5f), actual.card)
            assertEquals(lerp(start.onCard, end.onCard, 0.5f), actual.onCard)
            assertEquals(lerp(start.cardOutline, end.cardOutline, 0.5f), actual.cardOutline)
        }
    }
}
