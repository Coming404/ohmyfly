// 描边沿同一连续曲率路径内缩，动画值只在绘制阶段读取。
package top.yukonga.miuix.kmp.squircle

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp

@Composable
fun Modifier.squircleBorder(width: Dp, color: Color, cornerRadius: Dp,
    extension: Float = SquircleDefaults.Extension): Modifier =
    squircleBorder({ width }, { color }, cornerRadius, extension)

@Composable
fun Modifier.squircleBorder(width: () -> Dp, color: () -> Color, cornerRadius: Dp,
    extension: Float = SquircleDefaults.Extension): Modifier {
    val enabled = isSquircleEnabled()
    return drawWithCache {
        val path = Path()
        onDrawBehind {
            val strokeWidth = width().toPx()
            val innerWidth = size.width - strokeWidth
            val innerHeight = size.height - strokeWidth
            if (strokeWidth <= 0f || innerWidth <= 0f || innerHeight <= 0f) return@onDrawBehind
            val inset = strokeWidth / 2f
            path.rewind()
            path.addSquircleRect(innerWidth, innerHeight, (cornerRadius.toPx() - inset).coerceAtLeast(0f), extension, enabled)
            translate(inset, inset) { drawPath(path, color(), style = Stroke(strokeWidth)) }
        }
    }
}
