// 直接从共享 G2 轮廓读取贝塞尔控制点，避免裁剪与 shader 各算一套曲线。
package top.yukonga.miuix.kmp.material

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ohmyfly.shapes.continuousCorners

/** 缓存的是几何，不包含主题、图层或着色器实例。 */
internal class G2Silhouette(
    val outline: Outline,
    val curves: FloatArray,
)

/** 由实际像素半径构造同一份轮廓；密度在进入此处前已换算。 */
internal fun g2Silhouette(size: Size, start: Float, end: Float, bottomEnd: Float, bottomStart: Float,
    direction: LayoutDirection, continuous: Boolean): G2Silhouette {
    val shape = if (continuous) continuousCorners(start.dp, end.dp, bottomEnd.dp, bottomStart.dp)
        else RoundedCornerShape(start.dp, end.dp, bottomEnd.dp, bottomStart.dp)
    val outline = shape.createOutline(size, direction, Density(1f))
    if (outline !is Outline.Generic) return G2Silhouette(outline, EmptyG2Curves)
    val curves = FloatArray(G2CurveCount * G2CurveValues)
    val iterator = outline.path.iterator()
    val points = FloatArray(G2CurveValues)
    var count = 0
    while (iterator.hasNext()) {
        if (iterator.next(points) == PathSegment.Type.Cubic) {
            check(count < G2CurveCount) { "G2 轮廓曲线数量超出固定几何契约" }
            points.copyInto(curves, count * G2CurveValues)
            count++
        }
    }
    check(count == G2CurveCount) { "G2 轮廓必须包含四角各三段三次曲线" }
    return G2Silhouette(outline, curves)
}

/** 同一表面在模糊、边缘、遮罩和阴影阶段共享控制点。 */
internal class G2SilhouetteCache {
    private var size = Size.Unspecified
    private var start = Float.NaN
    private var end = Float.NaN
    private var bottomEnd = Float.NaN
    private var bottomStart = Float.NaN
    private var direction = LayoutDirection.Ltr
    private var continuous = false
    private var value: G2Silhouette? = null

    /** 稳定几何直接复用，不在绘制阶段创建列表或装箱键。 */
    fun resolve(size: Size, start: Float, end: Float, bottomEnd: Float, bottomStart: Float,
        direction: LayoutDirection, continuous: Boolean): G2Silhouette {
        val cached = value
        if (cached != null && this.size == size && this.start == start && this.end == end &&
            this.bottomEnd == bottomEnd && this.bottomStart == bottomStart &&
            this.direction == direction && this.continuous == continuous) return cached
        return g2Silhouette(size, start, end, bottomEnd, bottomStart, direction, continuous).also {
            this.size = size
            this.start = start
            this.end = end
            this.bottomEnd = bottomEnd
            this.bottomStart = bottomStart
            this.direction = direction
            this.continuous = continuous
            value = it
        }
    }
}

internal const val G2CurveCount = 12
internal const val G2CurveValues = 8
internal val EmptyG2Curves = FloatArray(G2CurveCount * G2CurveValues)
