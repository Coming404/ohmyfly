// 官方玻璃形状的 G2 补丁，公开类型与函数签名保持不变。
@file:kotlin.jvm.JvmName("GlassShapeKt")
package top.yukonga.miuix.kmp.material

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/** 0 使用圆弧；大于 0 使用共享 G2 轮廓。真圆与胶囊始终保持圆弧。 */
@Immutable
class GlassShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    val smoothing: Float = 1f,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        return geometry(size, topStart, topEnd, bottomEnd, bottomStart, layoutDirection).outline
    }

    private val geometryCache = G2SilhouetteCache()

    /** 布局裁剪与所有材质通道共用同一份 G2 路径。 */
    internal fun geometry(size: Size, start: Float, end: Float, bottomEnd: Float, bottomStart: Float,
        direction: LayoutDirection): G2Silhouette {
        val limit = size.minDimension * 0.5f
        return geometryCache.resolve(size, start.coerceIn(0f, limit), end.coerceIn(0f, limit),
            bottomEnd.coerceIn(0f, limit), bottomStart.coerceIn(0f, limit), direction, smoothing > 0f)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): GlassShape = GlassShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlassShape) return false
        return topStart == other.topStart &&
            topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd &&
            bottomStart == other.bottomStart &&
            smoothing == other.smoothing
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + smoothing.hashCode()
        return result
    }

    override fun toString(): String = "GlassShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, " +
        "bottomStart=$bottomStart, smoothing=$smoothing)"
}

/**
 * Creates a [GlassShape] with the same radius at all four corners.
 *
 * @param cornerRadius Radius applied to every corner.
 * @param smoothing 0 使用圆弧，大于 0 使用共享 G2 轮廓。
 */
fun GlassShape(cornerRadius: Dp, smoothing: Float = GlassDefaults.Smoothing): GlassShape = GlassShape(
    CornerSize(cornerRadius),
    CornerSize(cornerRadius),
    CornerSize(cornerRadius),
    CornerSize(cornerRadius),
    smoothing,
)

/**
 * Creates a [GlassShape] with an individual radius for each corner, in the order used by
 * `RoundedCornerShape`.
 *
 * @param topStart Radius of the top-start corner.
 * @param topEnd Radius of the top-end corner.
 * @param bottomEnd Radius of the bottom-end corner.
 * @param bottomStart Radius of the bottom-start corner.
 * @param smoothing 0 使用圆弧，大于 0 使用共享 G2 轮廓。
 */
fun GlassShape(
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp,
    smoothing: Float = GlassDefaults.Smoothing,
): GlassShape = GlassShape(
    CornerSize(topStart),
    CornerSize(topEnd),
    CornerSize(bottomEnd),
    CornerSize(bottomStart),
    smoothing,
)
