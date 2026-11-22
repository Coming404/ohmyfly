// 页面弹层保留既有状态与生命周期，仅将表面交给官方玻璃组件。
package com.ohmyfly.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ohmyfly.ui.glass.glassEffects
import com.ohmyfly.ui.glass.glassPreset
import com.ohmyfly.ui.glass.rememberGlassMaterialColors
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheetDefaults
import top.yukonga.miuix.kmp.glass.GlassDialogDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.layout.BottomSheetSurfaceScope
import top.yukonga.miuix.kmp.layout.DialogSurfaceScope
import top.yukonga.miuix.kmp.material.GlassShape

/** 抽屉保留 Miuix 宿主动画和 scrim state，表面走 Clear face shader。 */
@Composable
internal fun appBottomSheetSurface(): (@Composable BottomSheetSurfaceScope.() -> Modifier)? {
    val appearance = LocalChromeAppearance.current
    val backdrop = appearance.backdrop ?: return null
    val material = GlassBottomSheetDefaults.material().withSettings(appearance.materialSettings)
    val colors = rememberGlassMaterialColors(material)
    val preset = glassPreset()
    return {
        val clearBackdrop = remember(backdrop, scrimColor) { ClearScrimBackdrop(backdrop, scrimColor) }
        val shape = remember(cornerRadius, isOverscroll) {
            GlassShape(
                topStart = if (isOverscroll) 0.dp else cornerRadius,
                topEnd = if (isOverscroll) 0.dp else cornerRadius,
                bottomEnd = 0.dp,
                bottomStart = 0.dp,
            )
        }
        Modifier.drawBackdrop(
            backdrop = clearBackdrop,
            shape = { shape },
            effects = { glassEffects(preset, material, colors) },
        )
    }
}

/** 对话框保留 Miuix host scaling/scrim state，采样层使用 Clear face shader。 */
@Composable
internal fun appDialogSurface(): (@Composable DialogSurfaceScope.() -> Modifier)? {
    val appearance = LocalChromeAppearance.current
    val backdrop = appearance.backdrop ?: return null
    val material = GlassDialogDefaults.material().withSettings(appearance.materialSettings)
    val colors = rememberGlassMaterialColors(material)
    val preset = glassPreset()
    return {
        val clearBackdrop = remember(backdrop, scrimColor, scale) {
            ClearScrimBackdrop(backdrop, scrimColor, scale)
        }
        val shape = remember(cornerRadius) { GlassShape(cornerRadius) }
        Modifier.drawBackdrop(
            backdrop = clearBackdrop,
            shape = { shape },
            effects = { glassEffects(preset, material, colors) },
        )
    }
}

/** 菜单沿用既有定位与关闭状态；光学轮廓由本地材质补丁统一。 */
@Composable
internal fun appPopupSurface(): (@Composable (Shape) -> Modifier)? {
    val appearance = LocalChromeAppearance.current
    val backdrop = appearance.backdrop ?: return null
    val material = GlassPopupDefaults.material().withSettings(appearance.materialSettings)
    val colors = rememberGlassMaterialColors(material)
    val preset = glassPreset()
    return { shape ->
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { glassEffects(preset, material, colors) },
        )
    }
}

/** 在 blur 前合成 Miuix 弹层 scrim，并反向抵消 dialog scale。 */
@Stable
private class ClearScrimBackdrop(
    private val source: Backdrop,
    private val scrimColor: State<Color>,
    private val scale: State<Float>? = null,
) : Backdrop by source {
    private val scaleLayerBlock: GraphicsLayerScope.() -> Unit = {
        scale?.let { scaleX *= it.value; scaleY *= it.value }
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        val samplingLayer = when {
            scale == null -> layerBlock
            layerBlock == null -> scaleLayerBlock
            else -> {
                {
                    layerBlock.invoke(this)
                    scaleLayerBlock.invoke(this)
                }
            }
        }
        with(source) {
            drawBackdrop(density, coordinates, samplingLayer, downscaleFactor)
        }
        val scrim = scrimColor.value
        if (scrim.alpha > 0f) {
            val padding = (density as? BackdropEffectScope)?.padding ?: 0f
            val scaledPadding = padding / downscaleFactor
            drawRect(scrim, Offset(-scaledPadding, -scaledPadding), size, blendMode = BlendMode.SrcAtop)
        }
    }
}