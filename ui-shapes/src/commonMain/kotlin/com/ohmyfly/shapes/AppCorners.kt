// 应用与组件库共用的连续曲率形状和按职责分级的半径。
package com.ohmyfly.shapes

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.UnevenRoundedRectangle

/** 数值只在此维护；嵌套内容按外层半径减去实际间距获得同心轮廓。 */
object AppCorners {
    val Card = 24.dp
    val SettingsCard = 16.dp
    val Dialog = 28.dp
    val Sheet = 28.dp
    val Control = 16.dp
    val Input = 16.dp
    val Notification = 16.dp
    val Toolbar = 50.dp
    val Selection = 18.dp
    val Content = 12.dp
    val Compact = 8.dp
    val Skeleton = 4.dp
    val StopMark = 2.dp
    val Capsule: Shape = CircleShape
    val Circle: Shape = CircleShape

    fun inset(outer: Dp, spacing: Dp): Dp = (outer - spacing).coerceAtLeast(0.dp)
    fun shape(radius: Dp): Shape = ContinuousShape(radius)
    fun tile(size: Dp): Shape = shape(size * 0.3f)
}

/** 半径达到半短边时使用真圆或真胶囊；普通圆角委托 G2 曲线，不重复实现几何。 */
class ContinuousShape(val radius: Dp) : Shape {
    private val continuous = RoundedRectangle(radius, RoundedCornerStyle.Continuous)
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        if (with(density) { radius.toPx() } >= size.minDimension / 2f) {
            CircleShape.createOutline(size, layoutDirection, density)
        } else continuous.createOutline(size, layoutDirection, density)

    override fun equals(other: Any?): Boolean = other is ContinuousShape && radius == other.radius
    override fun hashCode(): Int = radius.hashCode()
}

/** 非对称圆角保留起止方向；抽屉贴底边不强行添加下圆角。 */
fun continuousCorners(topStart: Dp, topEnd: Dp, bottomEnd: Dp, bottomStart: Dp): Shape =
    if (topStart == topEnd && topEnd == bottomEnd && bottomEnd == bottomStart) {
        AppCorners.shape(topStart)
    } else UnevenRoundedRectangle(topStart, topEnd, bottomEnd, bottomStart, RoundedCornerStyle.Continuous)
