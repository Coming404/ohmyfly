// 路径接口供组件动画和描边复用，保持与背景裁剪相同的轮廓。
@file:Suppress("UNUSED_PARAMETER")
package top.yukonga.miuix.kmp.squircle

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ohmyfly.shapes.AppCorners

/** 这些默认值保留预编译组件 ABI，连续曲率算法不使用旧 extension 系数。 */
object SquircleDefaults {
    val Extension = 1.1f
    val ExtensionMin = 1f
    val ExtensionMax = 2f
}

fun Path.addSquircleRect(width: Float, height: Float, cornerRadius: Float,
    extension: Float = SquircleDefaults.Extension, squircleEnabled: Boolean = true) {
    if (width <= 0f || height <= 0f) return
    val radius = cornerRadius.coerceAtLeast(0f).dp
    val shape = if (squircleEnabled) AppCorners.shape(radius) else RoundedCornerShape(radius)
    when (val outline = shape.createOutline(Size(width, height), LayoutDirection.Ltr, Density(1f))) {
        is Outline.Generic -> addPath(outline.path)
        is Outline.Rounded -> addRoundRect(outline.roundRect)
        is Outline.Rectangle -> addRect(outline.rect)
    }
}
