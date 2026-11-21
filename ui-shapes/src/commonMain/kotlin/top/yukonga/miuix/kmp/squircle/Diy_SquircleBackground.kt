// 保留组件库公开修饰器签名，背景和裁剪统一交给连续曲率形状。
@file:Suppress("UNUSED_PARAMETER")
@file:kotlin.jvm.JvmName("SquircleBackgroundKt")
package top.yukonga.miuix.kmp.squircle

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.ohmyfly.shapes.continuousCorners

// extension 为预编译控件保留；曲率由统一的 G2 几何决定，不再改变半径。
@Composable
fun Modifier.squircleBackground(color: Color, cornerRadius: Dp, extension: Float = SquircleDefaults.Extension): Modifier =
    squircleBackground(color, cornerRadius, cornerRadius, cornerRadius, cornerRadius, extension)

@Composable
fun Modifier.squircleBackground(color: Color, topStart: Dp, topEnd: Dp, bottomEnd: Dp, bottomStart: Dp,
    extension: Float = SquircleDefaults.Extension): Modifier =
    background(color, rememberCorners(topStart, topEnd, bottomEnd, bottomStart))

@Composable
fun Modifier.squircleClip(cornerRadius: Dp, extension: Float = SquircleDefaults.Extension): Modifier =
    squircleClip(cornerRadius, cornerRadius, cornerRadius, cornerRadius, extension)

@Composable
fun Modifier.squircleClip(topStart: Dp, topEnd: Dp, bottomEnd: Dp, bottomStart: Dp,
    extension: Float = SquircleDefaults.Extension): Modifier =
    clip(rememberCorners(topStart, topEnd, bottomEnd, bottomStart))

@Composable
fun Modifier.squircleSurface(color: Color, cornerRadius: Dp, extension: Float = SquircleDefaults.Extension): Modifier =
    squircleSurface(color, cornerRadius, cornerRadius, cornerRadius, cornerRadius, extension)

@Composable
fun Modifier.squircleSurface(color: Color, topStart: Dp, topEnd: Dp, bottomEnd: Dp, bottomStart: Dp,
    extension: Float = SquircleDefaults.Extension): Modifier =
    clip(rememberCorners(topStart, topEnd, bottomEnd, bottomStart)).background(color)

@Composable
fun Modifier.absoluteSquircleBackground(color: Color, topLeft: Dp, topRight: Dp, bottomRight: Dp, bottomLeft: Dp,
    extension: Float = SquircleDefaults.Extension): Modifier =
    background(color, AbsoluteCorners(rememberCorners(topLeft, topRight, bottomRight, bottomLeft)))

@Composable
fun Modifier.absoluteSquircleClip(topLeft: Dp, topRight: Dp, bottomRight: Dp, bottomLeft: Dp,
    extension: Float = SquircleDefaults.Extension): Modifier =
    clip(AbsoluteCorners(rememberCorners(topLeft, topRight, bottomRight, bottomLeft)))

@Composable
fun Modifier.absoluteSquircleSurface(color: Color, topLeft: Dp, topRight: Dp, bottomRight: Dp, bottomLeft: Dp,
    extension: Float = SquircleDefaults.Extension): Modifier =
    clip(AbsoluteCorners(rememberCorners(topLeft, topRight, bottomRight, bottomLeft))).background(color)

/** 位置式接口不随 RTL 翻转，方向式接口由形状本身处理布局方向。 */
private data class AbsoluteCorners(val shape: Shape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        shape.createOutline(size, LayoutDirection.Ltr, density)
}

@Composable
private fun rememberCorners(topStart: Dp, topEnd: Dp, bottomEnd: Dp, bottomStart: Dp): Shape {
    val enabled = isSquircleEnabled()
    return remember(topStart, topEnd, bottomEnd, bottomStart, enabled) {
        if (enabled) continuousCorners(topStart, topEnd, bottomEnd, bottomStart)
        else androidx.compose.foundation.shape.RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
    }
}
