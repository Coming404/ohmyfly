// 顶栏、工具栏与独立动作只使用官方 Miuix 玻璃渲染。
package com.ohmyfly.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.ohmyfly.ui.glass.rememberGlassStyle
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Expressive 与无障碍降级使用主题实色，不运行背景采样或旧折射着色器。 */
@Composable
internal fun Modifier.chromeMaterial(
    backdrop: Backdrop,
    enabled: Boolean,
    dark: Boolean,
): Modifier {
    val style = rememberGlassStyle(enabled, dark)
    val surface = MiuixTheme.colorScheme.surfaceContainer
    return glassPanel(
        backdrop = backdrop.takeIf { enabled },
        shape = NavigationBarShape,
        style = style.value,
        material = GlassTopAppBarDefaults.buttonMaterial().withSettings(LocalChromeAppearance.current.materialSettings),
        stroke = if (enabled) GlassEdgeStrokes.forTheme(dark) else null,
        shadow = GlassShadows.Regular,
        shading = enabled,
        fallback = Modifier.clip(NavigationBarShape).background(surface),
    )
}
