// 液态材质使用 Kyant 的圆角轮廓、原生折射与高光，交互状态沿用应用的手势策略。
package com.ohmyfly.ui.glass

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.ohmyfly.ui.NavigationBarShape
import com.ohmyfly.ui.glass.sensor.rememberGlassGravity
import kotlin.math.PI

/** Kyant 的折射需要能读取圆角半径的 Shape，不能传入旧着色器的 Generic Outline。 */
internal fun ChromeBackdrop.surfaceShape(shape: GlassShape): Shape = when {
    this !is ChromeBackdrop.Liquid -> shape
    shape === NavigationBarShape -> CircleShape
    else -> RoundedCornerShape(shape.radius)
}

@Composable
private fun rememberLiquidHighlight(preset: GlassPreset, motion: Boolean, offset: Float): State<Highlight> {
    val gravity = rememberGlassGravity(motion && preset.highlightAlpha > 0f)
    return remember(preset, gravity, offset) {
        derivedStateOf {
            val angle = -quantizedGravityAngle(gravity.value) * (180f / PI.toFloat()) + offset
            Highlight(width = 0.5.dp, blurRadius = preset.highlightBlur.dp, alpha = preset.highlightAlpha,
                style = HighlightStyle.Default(angle = angle))
        }
    }
}

/** 面板、标题和工具栏共享程序，只根据当前背景类型选择一个绘制后端。 */
@Composable
internal fun Modifier.glassSurface(
    backdrop: ChromeBackdrop,
    shape: GlassShape,
    preset: GlassPreset,
    tint: Color,
    motion: Boolean,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    pressProgress: () -> Float = { 0f },
): Modifier = when (backdrop) {
    is ChromeBackdrop.Standard -> glassSurface(backdrop.source, shape, preset, tint,
        rememberGlassHighlight(true, motion, preset), layerBlock)
    is ChromeBackdrop.Liquid -> {
        val light = rememberLiquidHighlight(preset, motion, -45f)
        val outline = remember(shape) { backdrop.surfaceShape(shape) }
        drawBackdrop(backdrop = backdrop.source, shape = { outline }, effects = {
            val press = pressProgress().coerceIn(0f, 1f)
            colorControls(brightness = preset.brightness, saturation = preset.saturation)
            blur(preset.blur.dp.toPx())
            lens((preset.lensHeight + 10f * press).dp.toPx(), (preset.lensAmount + 14f * press).dp.toPx(),
                depthEffect = press > 0f, chromaticAberration = press > 0f)
        }, highlight = { light.value }, shadow = null, layerBlock = layerBlock,
            onDrawSurface = { drawRect(tint.copy(alpha = preset.surfaceAlpha)) })
    }
}

/** 前景透镜采样已处理过的页面和标签，因此此处只增加按压折射。 */
@Composable
internal fun Modifier.liquidCapsule(
    source: Backdrop, response: GlassNavigationGesture, preset: GlassPreset, motion: Boolean,
    transform: GraphicsLayerScope.() -> Unit, dark: Boolean,
): Modifier {
    val light = rememberLiquidHighlight(preset, motion, 90f)
    return drawBackdrop(backdrop = source, shape = { CircleShape }, effects = {
        val press = response.drag.pressProgress
        lens(10.dp.toPx() * press, 14.dp.toPx() * press, depthEffect = true, chromaticAberration = press > 0f)
    }, highlight = { light.value.copy(alpha = light.value.alpha * response.drag.pressProgress) },
        shadow = null, innerShadow = {
            val press = response.drag.pressProgress
            InnerShadow(radius = 8.dp * press, color = Color.Black.copy(alpha = 0.15f), alpha = press)
        }, layerBlock = transform, onDrawSurface = {
            val press = response.drag.pressProgress
            drawRect((if (dark) Color.White else Color.Black).copy(alpha = 0.1f * (1f - press)))
            drawRect(Color.Black.copy(alpha = 0.03f * press))
        })
}
