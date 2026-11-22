// 页面弹层保留既有状态与生命周期，仅将表面交给官方玻璃组件。
package com.ohmyfly.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.ohmyfly.shapes.AppCorners
import top.yukonga.miuix.kmp.glass.GlassBottomSheetDefaults
import top.yukonga.miuix.kmp.glass.GlassDialogDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.glassBottomSheetSurface
import top.yukonga.miuix.kmp.glass.glassDialogSurface
import top.yukonga.miuix.kmp.glass.glassSurface
import top.yukonga.miuix.kmp.layout.BottomSheetSurfaceScope
import top.yukonga.miuix.kmp.layout.DialogSurfaceScope

/** 不透明模式使用原组件的 G2 背景，无额外录制层。 */
@Composable
internal fun appBottomSheetSurface(): (@Composable BottomSheetSurfaceScope.() -> Modifier)? {
    val appearance = LocalChromeAppearance.current
    val backdrop = appearance.backdrop ?: return null
    val material = GlassBottomSheetDefaults.material().withSettings(appearance.materialSettings)
    return glassBottomSheetSurface(backdrop, material = material)
}

/** 对话框遮罩由官方表面在采样前处理，避免内容被重复折射。 */
@Composable
internal fun appDialogSurface(): (@Composable DialogSurfaceScope.() -> Modifier)? {
    val appearance = LocalChromeAppearance.current
    val backdrop = appearance.backdrop ?: return null
    val material = GlassDialogDefaults.material().withSettings(appearance.materialSettings)
    return glassDialogSurface(backdrop, material = material)
}

/** 菜单沿用既有定位与关闭状态；光学轮廓由本地材质补丁统一。 */
@Composable
internal fun appPopupSurface(): (@Composable (Shape) -> Modifier)? {
    val appearance = LocalChromeAppearance.current
    val backdrop = appearance.backdrop ?: return null
    val material = GlassPopupDefaults.material().withSettings(appearance.materialSettings)
    val visuals = GlassPopupDefaults.visuals(material = material)
    return glassSurface(backdrop, visuals, AppCorners.Control)
}
