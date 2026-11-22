// 导航胶囊按压时绘制受形状裁切的内部环影。
package com.ohmyfly.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** 保存按压环影的模糊半径、偏移和可见度。 */
@Immutable
internal data class NavigationInnerShadow(
    val radius: Dp,
    val offset: DpOffset = DpOffset(0.dp, radius),
    val color: Color = Color.Black.copy(alpha = 0.15f),
    val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode,
)

/** 在玻璃胶囊的既有轮廓内叠加按压环影。 */
internal fun Modifier.navigationInnerShadow(
    shape: Shape,
    shadow: () -> NavigationInnerShadow?,
): Modifier = this then NavigationInnerShadowElement(shape, shadow)

/** 复用节点持有的离屏图层，按动画帧更新环影。 */
private class NavigationInnerShadowElement(
    val shape: Shape,
    val shadow: () -> NavigationInnerShadow?,
) : ModifierNodeElement<NavigationInnerShadowNode>() {

    override fun create(): NavigationInnerShadowNode = NavigationInnerShadowNode(shape, shadow)

    override fun update(node: NavigationInnerShadowNode) {
        node.shape = shape
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "navigationInnerShadow"
        properties["shape"] = shape
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean = other is NavigationInnerShadowElement &&
        shape == other.shape && shadow == other.shadow

    override fun hashCode(): Int = 31 * shape.hashCode() + shadow.hashCode()
}

/** 在目标轮廓内绘制按压内环阴影。 */
private class NavigationInnerShadowNode(
    var shape: Shape,
    var shadow: () -> NavigationInnerShadow?,
) : Modifier.Node(), DrawModifierNode {

    override val shouldAutoInvalidate: Boolean = false

    private var layer: GraphicsLayer? = null
    private val paint = Paint()
    private val clipPath = Path()
    private var previousRadius = Float.NaN

    /** 偏移并挖空原形状，再模糊剩余边缘形成内环。 */
    override fun ContentDrawScope.draw() {
        drawContent()
        val style = shadow() ?: return
        val shadowLayer = layer ?: return
        val radiusPx = style.radius.toPx()
        val offsetX = style.offset.x.toPx()
        val offsetY = style.offset.y.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        clipPath.reset()
        when (outline) {
            is Outline.Rectangle -> clipPath.addRect(outline.rect)
            is Outline.Rounded -> clipPath.addRoundRect(outline.roundRect)
            is Outline.Generic -> clipPath.addPath(outline.path)
        }
        paint.color = style.color
        shadowLayer.alpha = style.alpha
        shadowLayer.blendMode = style.blendMode
        if (previousRadius != radiusPx) {
            shadowLayer.renderEffect = if (radiusPx > 0f) {
                BlurEffect(radiusPx, radiusPx, TileMode.Decal)
            } else {
                null
            }
            previousRadius = radiusPx
        }
        shadowLayer.record {
            drawContext.canvas.let { canvas ->
                canvas.save()
                canvas.clipPath(clipPath)
                canvas.drawOutline(outline, paint)
                canvas.translate(offsetX, offsetY)
                canvas.drawOutline(outline, ClearPaint)
                canvas.translate(-offsetX, -offsetY)
                canvas.restore()
            }
        }
        drawContext.canvas.let { canvas ->
            canvas.save()
            canvas.clipPath(clipPath)
            drawLayer(shadowLayer)
            canvas.restore()
        }
    }

    /** 在当前绘制上下文内申请可复用图层。 */
    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    /** 节点脱离时归还它持有的离屏图层。 */
    override fun onDetach() {
        layer?.let {
            requireGraphicsContext().releaseGraphicsLayer(it)
            layer = null
        }
    }
}

private val ClearPaint = Paint().apply { blendMode = BlendMode.Clear }
