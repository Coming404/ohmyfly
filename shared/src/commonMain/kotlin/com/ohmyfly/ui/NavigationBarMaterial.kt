// 导航使用轻透材质，保留 Miuix 的模糊半径和主题混色方式。
package com.ohmyfly.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.material.GlassMaterial

private const val NavigationSecondLayerAlpha = 0.34f
private const val NavigationThirdLayerAlpha = 0.18f

/** 下调两层色片的 Alpha，让背景结构仍能透过玻璃显现。 */
@Composable
internal fun navigationBarMaterial(): GlassMaterial {
    val base = GlassNavigationBarDefaults.material()
    return remember(base) {
        base.copy(
            second = base.second?.let { it.copy(color = it.color.copy(alpha = NavigationSecondLayerAlpha)) },
            third = base.third?.let { it.copy(color = it.color.copy(alpha = NavigationThirdLayerAlpha)) },
        )
    }
}
