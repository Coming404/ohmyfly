package com.ohmyfly.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

internal fun appThemeController(dynamicColorSeed: Color?, dark: Boolean): ThemeController =
    ThemeController(
        colorSchemeMode = if (dynamicColorSeed != null) ColorSchemeMode.MonetSystem else ColorSchemeMode.System,
        keyColor = dynamicColorSeed,
        paletteStyle = ThemePaletteStyle.Neutral,
        colorSpec = ThemeColorSpec.Spec2025,
        isDark = dark,
    )

@Composable
internal fun AppTheme(dynamicColorSeed: Color?, dark: Boolean, content: @Composable () -> Unit) {
    val controller = remember(dark, dynamicColorSeed) {
        appThemeController(dynamicColorSeed, dark)
    }
    MiuixTheme(controller = controller) {
        val motionAllowed = LocalAppMotionAllowed.current
        val rootColors = MiuixTheme.colorScheme
        val target = remember(controller) { rootColors.copy() }
        val colors = rememberAnimatedThemeColors(target, motionAllowed)
        val surfaces = rememberAnimatedAppSurfaceColors(
            appSurfaceColors(target, dynamicColorSeed != null, dark), motionAllowed,
        )
        // The colors overload inherits the root's dynamic-mode marker; it cannot provide it alone.
        MiuixTheme(colors = colors) {
            CompositionLocalProvider(
                LocalAppSurfaceColors provides surfaces,
                LocalContentColor provides colors.onBackground,
                content = content,
            )
        }
    }
}
