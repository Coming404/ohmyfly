package dev.ohmydrift.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.extended.Link

/**
 * Icons for the mirror page. Sort and Link reuse the pinned Miuix 0.9.4 set; the rest are the
 * Lucide shapes the wireframe draws (docs/design/mirror-settings/index.html bundles Lucide).
 * Lucide is ISC-licensed, see docs/design/mirror-settings/licenses/lucide-LICENSE. They are
 * redrawn as Compose vectors here, so no extra icon dependency is added.
 */
internal object MirrorIcons {
    val Github = glyph("Github") {
        moveTo(15f, 22f)
        lineTo(15f, 18f)
        curveTo(15.139f, 16.747f, 14.78f, 15.49f, 14f, 14.5f)
        curveTo(17f, 14.5f, 20f, 12.5f, 20f, 9f)
        curveTo(20.08f, 7.75f, 19.73f, 6.52f, 19f, 5.5f)
        curveTo(19.28f, 4.35f, 19.28f, 3.15f, 19f, 2f)
        curveTo(19f, 2f, 18f, 2f, 16f, 3.5f)
        curveTo(13.36f, 3f, 10.64f, 3f, 8f, 3.5f)
        curveTo(6f, 2f, 5f, 2f, 5f, 2f)
        curveTo(4.7f, 3.15f, 4.7f, 4.35f, 5f, 5.5f)
        curveTo(4.272f, 6.516f, 3.918f, 7.753f, 4f, 9f)
        curveTo(4f, 12.5f, 7f, 14.5f, 10f, 14.5f)
        curveTo(9.61f, 14.99f, 9.32f, 15.55f, 9.15f, 16.15f)
        curveTo(8.98f, 16.75f, 8.93f, 17.38f, 9f, 18f)
        lineTo(9f, 22f)
        moveTo(9f, 18f)
        curveTo(4.49f, 20f, 4f, 16f, 2f, 16f)
    }

    val Power = glyph("Power") {
        moveTo(12f, 2f)
        lineTo(12f, 12f)
        moveTo(18.4f, 6.6f)
        curveTo(21.909f, 10.11f, 21.914f, 15.798f, 18.412f, 19.314f)
        curveTo(14.91f, 22.83f, 9.222f, 22.848f, 5.698f, 19.354f)
        curveTo(2.174f, 15.859f, 2.143f, 10.172f, 5.63f, 6.64f)
    }

    val List = glyph("List") {
        moveTo(3f, 12f)
        lineTo(3.01f, 12f)
        moveTo(3f, 18f)
        lineTo(3.01f, 18f)
        moveTo(3f, 6f)
        lineTo(3.01f, 6f)
        moveTo(8f, 12f)
        lineTo(21f, 12f)
        moveTo(8f, 18f)
        lineTo(21f, 18f)
        moveTo(8f, 6f)
        lineTo(21f, 6f)
    }

    val More = glyph("More") {
        moveTo(13f, 12f)
        curveTo(13f, 12.552f, 12.552f, 13f, 12f, 13f)
        curveTo(11.448f, 13f, 11f, 12.552f, 11f, 12f)
        curveTo(11f, 11.448f, 11.448f, 11f, 12f, 11f)
        curveTo(12.552f, 11f, 13f, 11.448f, 13f, 12f)
        close()
        moveTo(20f, 12f)
        curveTo(20f, 12.552f, 19.552f, 13f, 19f, 13f)
        curveTo(18.448f, 13f, 18f, 12.552f, 18f, 12f)
        curveTo(18f, 11.448f, 18.448f, 11f, 19f, 11f)
        curveTo(19.552f, 11f, 20f, 11.448f, 20f, 12f)
        close()
        moveTo(6f, 12f)
        curveTo(6f, 12.552f, 5.552f, 13f, 5f, 13f)
        curveTo(4.448f, 13f, 4f, 12.552f, 4f, 12f)
        curveTo(4f, 11.448f, 4.448f, 11f, 5f, 11f)
        curveTo(5.552f, 11f, 6f, 11.448f, 6f, 12f)
        close()
    }

    val Add = glyph("Add") {
        moveTo(5f, 12f)
        lineTo(19f, 12f)
        moveTo(12f, 5f)
        lineTo(12f, 19f)
    }

    val Play = glyph("Play") {
        moveTo(6f, 3f)
        lineTo(20f, 12f)
        lineTo(6f, 21f)
        lineTo(6f, 3f)
        close()
    }

    val Stop = glyph("Stop") {
        moveTo(22f, 12f)
        curveTo(22f, 17.523f, 17.523f, 22f, 12f, 22f)
        curveTo(6.477f, 22f, 2f, 17.523f, 2f, 12f)
        curveTo(2f, 6.477f, 6.477f, 2f, 12f, 2f)
        curveTo(17.523f, 2f, 22f, 6.477f, 22f, 12f)
        close()
        moveTo(10f, 9f)
        lineTo(14f, 9f)
        curveTo(14.55f, 9f, 15f, 9.45f, 15f, 10f)
        lineTo(15f, 14f)
        curveTo(15f, 14.55f, 14.55f, 15f, 14f, 15f)
        lineTo(10f, 15f)
        curveTo(9.45f, 15f, 9f, 14.55f, 9f, 14f)
        lineTo(9f, 10f)
        curveTo(9f, 9.45f, 9.45f, 9f, 10f, 9f)
        close()
    }

    val Edit = glyph("Edit") {
        moveTo(21.174f, 6.812f)
        curveTo(22.275f, 5.711f, 22.275f, 3.926f, 21.174f, 2.826f)
        curveTo(20.074f, 1.725f, 18.289f, 1.724f, 17.188f, 2.825f)
        lineTo(3.842f, 16.174f)
        curveTo(3.61f, 16.406f, 3.438f, 16.691f, 3.342f, 17.004f)
        lineTo(2.021f, 21.356f)
        curveTo(1.968f, 21.532f, 2.017f, 21.723f, 2.147f, 21.853f)
        curveTo(2.277f, 21.983f, 2.468f, 22.031f, 2.644f, 21.978f)
        lineTo(6.997f, 20.658f)
        curveTo(7.31f, 20.563f, 7.595f, 20.392f, 7.827f, 20.161f)
        close()
        moveTo(15f, 5f)
        lineTo(19f, 9f)
    }

    val Up = glyph("Up") {
        moveTo(5f, 12f)
        lineTo(12f, 5f)
        lineTo(19f, 12f)
        moveTo(12f, 19f)
        lineTo(12f, 5f)
    }

    val Down = glyph("Down") {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(19f, 12f)
        lineTo(12f, 19f)
        lineTo(5f, 12f)
    }

    val Trash = glyph("Trash") {
        moveTo(3f, 6f)
        lineTo(21f, 6f)
        moveTo(19f, 6f)
        lineTo(19f, 20f)
        curveTo(19f, 21f, 18f, 22f, 17f, 22f)
        lineTo(7f, 22f)
        curveTo(6f, 22f, 5f, 21f, 5f, 20f)
        lineTo(5f, 6f)
        moveTo(8f, 6f)
        lineTo(8f, 4f)
        curveTo(8f, 3f, 9f, 2f, 10f, 2f)
        lineTo(14f, 2f)
        curveTo(15f, 2f, 16f, 3f, 16f, 4f)
        lineTo(16f, 6f)
        moveTo(10f, 11f)
        lineTo(10f, 17f)
        moveTo(14f, 11f)
        lineTo(14f, 17f)
    }

    val Shield = glyph("Shield") {
        moveTo(20f, 13f)
        curveTo(20f, 18f, 16.5f, 20.5f, 12.34f, 21.95f)
        curveTo(12.122f, 22.024f, 11.886f, 22.02f, 11.67f, 21.94f)
        curveTo(7.5f, 20.5f, 4f, 18f, 4f, 13f)
        lineTo(4f, 6f)
        curveTo(4f, 5.448f, 4.448f, 5f, 5f, 5f)
        curveTo(7f, 5f, 9.5f, 3.8f, 11.24f, 2.28f)
        curveTo(11.678f, 1.906f, 12.322f, 1.906f, 12.76f, 2.28f)
        curveTo(14.51f, 3.81f, 17f, 5f, 19f, 5f)
        curveTo(19.552f, 5f, 20f, 5.448f, 20f, 6f)
        close()
        moveTo(9f, 12f)
        lineTo(11f, 14f)
        lineTo(15f, 10f)
    }

    val Name = glyph("Name") {
        moveTo(4f, 7f)
        lineTo(4f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 7f)
        moveTo(9f, 20f)
        lineTo(15f, 20f)
        moveTo(12f, 4f)
        lineTo(12f, 20f)
    }

    val Globe = glyph("Globe") {
        moveTo(22f, 12f)
        curveTo(22f, 17.523f, 17.523f, 22f, 12f, 22f)
        curveTo(6.477f, 22f, 2f, 17.523f, 2f, 12f)
        curveTo(2f, 6.477f, 6.477f, 2f, 12f, 2f)
        curveTo(17.523f, 2f, 22f, 6.477f, 22f, 12f)
        close()
        moveTo(12f, 2f)
        curveTo(6.667f, 7.6f, 6.667f, 16.4f, 12f, 22f)
        curveTo(17.333f, 16.4f, 17.333f, 7.6f, 12f, 2f)
        moveTo(2f, 12f)
        lineTo(22f, 12f)
    }

    val External = glyph("External") {
        moveTo(15f, 3f)
        lineTo(21f, 3f)
        lineTo(21f, 9f)
        moveTo(10f, 14f)
        lineTo(21f, 3f)
        moveTo(18f, 13f)
        lineTo(18f, 19f)
        curveTo(18f, 20.105f, 17.105f, 21f, 16f, 21f)
        lineTo(5f, 21f)
        curveTo(3.895f, 21f, 3f, 20.105f, 3f, 19f)
        lineTo(3f, 8f)
        curveTo(3f, 6.895f, 3.895f, 6f, 5f, 6f)
        lineTo(11f, 6f)
    }

    val Alert = glyph("Alert") {
        moveTo(22f, 12f)
        curveTo(22f, 17.523f, 17.523f, 22f, 12f, 22f)
        curveTo(6.477f, 22f, 2f, 17.523f, 2f, 12f)
        curveTo(2f, 6.477f, 6.477f, 2f, 12f, 2f)
        curveTo(17.523f, 2f, 22f, 6.477f, 22f, 12f)
        close()
        moveTo(12f, 8f)
        lineTo(12f, 12f)
        moveTo(12f, 16f)
        lineTo(12.01f, 16f)
    }

    /** Tri-state sort arrow; the state itself is exposed through the button semantics. */
    val Sort = MiuixIcons.Basic.ArrowUpDown
    val Link = MiuixIcons.Link

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
