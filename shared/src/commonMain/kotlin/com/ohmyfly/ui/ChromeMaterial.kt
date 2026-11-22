// 顶栏和工具栏使用 Clear 材质，并与底部导航共用同一玻璃 shader 参数。
package com.ohmyfly.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.ohmyfly.ui.glass.glassPreset
import com.ohmyfly.ui.glass.glassSurface
import com.ohmyfly.ui.glass.rememberGlassMaterialColors
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Expressive 与无障碍降级使用实色；LiquidGlass 复用导航 Clear 参数。 */
@Composable
internal fun Modifier.chromeMaterial(
    backdrop: Backdrop,
    enabled: Boolean,
    dark: Boolean,
): Modifier {
    val appearance = LocalChromeAppearance.current
    val material = GlassTopAppBarDefaults.buttonMaterial().withSettings(appearance.materialSettings)
    val colors = rememberGlassMaterialColors(material)
    return if (enabled) {
        glassSurface(
            backdrop = backdrop,
            shape = NavigationBarShape,
            preset = glassPreset(),
            material = material,
            colors = colors,
        )
    } else {
        clip(NavigationBarShape).background(MiuixTheme.colorScheme.surfaceContainer)
    }
}
