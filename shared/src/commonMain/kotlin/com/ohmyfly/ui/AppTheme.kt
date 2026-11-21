// 共享配色入口：玻璃保留中性动态色，Expressive 使用官方表达性色板。
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
import top.yukonga.miuix.kmp.theme.lightColorScheme
import com.ohmyfly.settings.MaterialMode

/** iOS 没有系统动态种子时，Expressive 从 Miuix 默认主色派生。 */
internal fun appThemeController(
    dynamicColorSeed: Color?,
    dark: Boolean,
    mode: MaterialMode = MaterialMode.LiquidGlass,
): ThemeController {
    val expressive = mode == MaterialMode.Expressive
    val seed = dynamicColorSeed ?: if (expressive) lightColorScheme().primary else null
    return ThemeController(
        colorSchemeMode = if (seed != null) ColorSchemeMode.MonetSystem else ColorSchemeMode.System,
        keyColor = seed,
        paletteStyle = if (expressive) ThemePaletteStyle.Expressive else ThemePaletteStyle.Neutral,
        colorSpec = ThemeColorSpec.Spec2025,
        isDark = dark,
    )
}

/** 模式切换复用现有颜色动画和全局动态效果策略。 */
@Composable
internal fun AppTheme(dynamicColorSeed: Color?, dark: Boolean, mode: MaterialMode, content: @Composable () -> Unit) {
    val controller = remember(dark, dynamicColorSeed, mode) {
        appThemeController(dynamicColorSeed, dark, mode)
    }
    MiuixTheme(controller = controller) {
        val motionAllowed = LocalAppMotionAllowed.current
        val rootColors = MiuixTheme.colorScheme
        val target = remember(controller) { rootColors.copy() }
        val colors = rememberAnimatedThemeColors(target, motionAllowed)
        val surfaces = rememberAnimatedAppSurfaceColors(
            appSurfaceColors(target, controller.keyColor != null, dark), motionAllowed,
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
