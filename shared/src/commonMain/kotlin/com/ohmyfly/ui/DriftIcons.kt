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

/** Shared Miuix icons from the complete library; only the Wave brand mark is original. */
internal object DriftIcons {
    val Overview = MiuixIcons.Home
    val Nodes = MiuixIcons.Layers
    val Subscriptions = MiuixIcons.Link
    val Settings = MiuixIcons.Settings
    val Speed = MiuixIcons.Stopwatch
    val Clear = MiuixIcons.Basic.Close
    val Check = MiuixIcons.Basic.Check
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
