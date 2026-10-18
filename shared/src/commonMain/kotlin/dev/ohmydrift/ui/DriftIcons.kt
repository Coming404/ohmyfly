package dev.ohmydrift.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small original line glyphs; no font, remote asset or additional icon library. */
internal object DriftIcons {
    val Overview = glyph("Overview") {
        moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
        moveTo(5f, 9f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 9f)
        moveTo(9f, 21f); lineTo(9f, 14f); lineTo(15f, 14f); lineTo(15f, 21f)
    }
    val Nodes = glyph("Nodes") {
        moveTo(5f, 4f); lineTo(19f, 4f); lineTo(19f, 10f); lineTo(5f, 10f); close()
        moveTo(5f, 14f); lineTo(19f, 14f); lineTo(19f, 20f); lineTo(5f, 20f); close()
        moveTo(8f, 7f); lineTo(9f, 7f)
        moveTo(8f, 17f); lineTo(9f, 17f)
    }
    val Theme = glyph("Theme") {
        moveTo(12f, 3f)
        curveTo(7f, 3f, 3f, 7f, 3f, 12f)
        curveTo(3f, 17f, 7f, 21f, 12f, 21f)
        curveTo(14f, 21f, 15f, 20f, 14f, 18f)
        curveTo(13f, 16f, 15f, 15f, 17f, 15f)
        curveTo(23f, 15f, 22f, 3f, 12f, 3f); close()
        moveTo(7f, 11f); lineTo(8f, 11f)
        moveTo(10f, 7f); lineTo(11f, 7f)
        moveTo(15f, 7f); lineTo(16f, 7f)
    }
    val Play = glyph("Play") {
        moveTo(8f, 4f); lineTo(20f, 12f); lineTo(8f, 20f); close()
    }
    val Arrow = glyph("Arrow") {
        moveTo(5f, 12f); lineTo(19f, 12f)
        moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
    }
    val Clear = glyph("Clear") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }
    val Check = glyph("Check") {
        moveTo(5f, 12f); lineTo(10f, 17f); lineTo(20f, 6f)
    }
    val Wave = glyph("Wave") {
        moveTo(3f, 8f)
        curveTo(6f, 3f, 9f, 13f, 12f, 8f)
        curveTo(15f, 3f, 18f, 13f, 21f, 8f)
        moveTo(3f, 16f)
        curveTo(6f, 11f, 9f, 21f, 12f, 16f)
        curveTo(15f, 11f, 18f, 21f, 21f, 16f)
    }

    private fun glyph(name: String, draw: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = draw,
            )
        }.build()
}
