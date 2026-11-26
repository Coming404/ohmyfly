// 列表内独立按钮复用导航材质，但只采样纯背景，避免录制图层包含自身。
package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ohmyfly.settings.GlassMaterialSettings
import com.ohmyfly.settings.MaterialMode
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TooltipBox

internal data class ChromeAppearance(
    val enabled: Boolean,
    val dark: Boolean,
    val mode: MaterialMode,
    val materialSettings: GlassMaterialSettings = GlassMaterialSettings(),
    val systemReduceTransparency: Boolean = false,
)
internal val LocalChromeAppearance = staticCompositionLocalOf<ChromeAppearance> { error("界面材质尚未初始化") }

/** 顶部和底栏采样整页，内联动作只采样它所在的实际底色。 */
@Composable
internal fun InlineChromeIconButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false,
    background: Color = LocalAppSurfaceColors.current.page,
    controlSize: Dp = ChromeMetrics.ControlSize,
    iconSize: Dp = ChromeMetrics.ToolbarIconSize,
) {
    val style = LocalChromeAppearance.current
    val backdrop = rememberLayerBackdrop { drawRect(background) }
    TooltipBox(text = label) {
        Box {
            Box(Modifier.matchParentSize().then(if (style.enabled) Modifier.layerBackdrop(backdrop) else Modifier))
            IconButton(onClick = hapticClick(onClick), enabled = enabled,
                modifier = Modifier.size(ChromeMetrics.ControlSize)
                    .chromeMaterial(backdrop, style.enabled, style.dark, style.mode),
                backgroundColor = Color.Transparent, cornerRadius = AppCorners.Toolbar,
            ) {
                if (busy && LocalAppMotionAllowed.current) CircularProgressIndicator(
                    size = 18.dp, modifier = Modifier.semantics { contentDescription = label },
                ) else Icon(icon, label, Modifier.size(ChromeMetrics.ToolbarIconSize),
                    tint = appIconTint().let { if (enabled) it else it.copy(alpha = 0.38f) })
            }
        }
    }
}
