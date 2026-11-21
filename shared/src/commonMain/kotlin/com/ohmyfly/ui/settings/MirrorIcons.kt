// 共享图标入口，集中维护官方图标与业务名称的对应。
package com.ohmyfly.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Edit
import top.yukonga.miuix.kmp.icon.glass.Error
import top.yukonga.miuix.kmp.icon.glass.Link
import top.yukonga.miuix.kmp.icon.glass.ListView
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.MoreConnections
import top.yukonga.miuix.kmp.icon.glass.NewWindow
import top.yukonga.miuix.kmp.icon.glass.Play
import top.yukonga.miuix.kmp.icon.glass.Power
import top.yukonga.miuix.kmp.icon.glass.Prohibit
import top.yukonga.miuix.kmp.icon.glass.Rename
import top.yukonga.miuix.kmp.icon.glass.Security

/** 镜像操作统一采用 Glass 图标，仅保留 GitHub 品牌图形。 */
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

    val Power = MiuixIcons.Glass.Power
    val List = MiuixIcons.Glass.ListView
    val More = MiuixIcons.Glass.More
    val Add = MiuixIcons.Glass.Add
    val Play = MiuixIcons.Glass.Play
    val Stop = MiuixIcons.Glass.Prohibit
    val Edit = MiuixIcons.Glass.Edit
    val Trash = MiuixIcons.Glass.Delete
    val Shield = MiuixIcons.Glass.Security
    val Name = MiuixIcons.Glass.Rename
    val Globe = MiuixIcons.Glass.MoreConnections
    val External = MiuixIcons.Glass.NewWindow
    val Alert = MiuixIcons.Glass.Error
    val Link = MiuixIcons.Glass.Link

    /** 品牌图形保持原有身份，不用通用 UI 符号替换。 */
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
