package com.ohmyfly.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Stopwatch

/** Shared Miuix icons from the complete library; only the BrandMark is original. */
internal object DriftIcons {
    val Overview = MiuixIcons.Home
    val Nodes = MiuixIcons.Layers
    val Subscriptions = MiuixIcons.Link
    val Settings = MiuixIcons.Settings
    val Speed = MiuixIcons.Stopwatch
    val Clear = MiuixIcons.Basic.Close
    val Check = MiuixIcons.Basic.Check

    /** Hexagonal cube from the app icon: thin outer hull, bold inner ring. */
    val BrandMark: ImageVector = ImageVector.Builder("BrandMark", 24.dp, 24.dp, 24f, 24f)
        .stroke(1.15f) {
            hexagon(10f)
            moveTo(3.34f, 7f); lineTo(7.67f, 9.5f)
            moveTo(20.66f, 7f); lineTo(16.33f, 9.5f)
            moveTo(12f, 17f); lineTo(12f, 22f)
        }
        .stroke(2.4f) { hexagon(5f) }
        .build()

    /** Pointy-top hexagon centred in the 24 grid; [r] is the centre-to-vertex radius. */
    private fun PathBuilder.hexagon(r: Float) {
        val dx = r * 0.866f
        moveTo(12f, 12f - r)
        lineTo(12f + dx, 12f - r / 2)
        lineTo(12f + dx, 12f + r / 2)
        lineTo(12f, 12f + r)
        lineTo(12f - dx, 12f + r / 2)
        lineTo(12f - dx, 12f - r / 2)
        close()
    }

    private fun ImageVector.Builder.stroke(width: Float, draw: PathBuilder.() -> Unit) = path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = draw,
    )
}
