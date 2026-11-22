// Liquid Glass 默认采用 Glasmio Clear 可映射的低填充材质。
package com.ohmyfly.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ohmyfly.settings.GlassMaterialSettings
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.material.GlassMaterial

/** 以当前主题的导航配方为底，应用 Clear 基线和持久化滑块值。 */
@Composable
internal fun navigationBarBaseMaterial(): GlassMaterial =
    GlassNavigationBarDefaults.material().withSettings(GlassMaterialSettings())

/** 导航主体和选中胶囊共用同一材质数据。 */
@Composable
internal fun navigationBarMaterial(): GlassMaterial {
    val base = navigationBarBaseMaterial()
    val settings = LocalChromeAppearance.current.materialSettings
    return remember(base, settings) { base.withSettings(settings) }
}
