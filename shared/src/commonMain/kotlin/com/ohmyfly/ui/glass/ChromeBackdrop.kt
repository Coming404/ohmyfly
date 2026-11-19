// 录制端与采样端必须使用同一引擎，切换材质时只创建当前引擎的图层。
package com.ohmyfly.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import com.kyant.backdrop.Backdrop as LiquidBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop as recordLiquid
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberLiquidLayer
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop as combineLiquid
import com.ohmyfly.settings.MaterialMode
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop as recordMiuix
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixLayer

internal sealed interface ChromeBackdrop {
    class Liquid(val source: LiquidBackdrop) : ChromeBackdrop
    class Standard(val source: MiuixBackdrop) : ChromeBackdrop
}

internal class ChromeRecording(val backdrop: ChromeBackdrop, val record: Modifier)

@Composable
internal fun rememberChromeRecording(mode: MaterialMode, draw: ContentDrawScope.() -> Unit): ChromeRecording =
    if (mode == MaterialMode.LiquidGlass) {
        val layer = rememberLiquidLayer(onDraw = draw)
        remember(layer) { ChromeRecording(ChromeBackdrop.Liquid(layer), Modifier.recordLiquid(layer)) }
    } else {
        val layer = rememberMiuixLayer { draw() }
        ChromeRecording(ChromeBackdrop.Standard(layer), Modifier.recordMiuix(layer))
    }

/** 选中透镜采样页面和隐藏文字行，隐藏行本身只录制一次。 */
@Composable
internal fun rememberTabRecording(page: ChromeBackdrop): ChromeRecording = when (page) {
    is ChromeBackdrop.Liquid -> {
        val tabs = rememberLiquidLayer()
        ChromeRecording(ChromeBackdrop.Liquid(combineLiquid(page.source, tabs)), Modifier.recordLiquid(tabs))
    }
    is ChromeBackdrop.Standard -> {
        val tabs = rememberMiuixLayer()
        ChromeRecording(ChromeBackdrop.Standard(rememberCombinedBackdrop(page.source, tabs)), Modifier.recordMiuix(tabs))
    }
}
