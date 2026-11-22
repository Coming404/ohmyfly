// 日志与设置复用固定版本图标，文件轮廓只用于日志列表。
package com.ohmyfly.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.UploadCloud

/** Aliases keep the settings vocabulary on the already installed Miuix icon set. */
internal object SettingsIcons {
    val File = ImageVector.Builder("LogFile", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f) {
            moveTo(14f, 3f); lineTo(6f, 3f); lineTo(6f, 21f); lineTo(19f, 21f)
            lineTo(19f, 8f); lineTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
            moveTo(9f, 12f); lineTo(16f, 12f)
            moveTo(9f, 16f); lineTo(16f, 16f)
        }
    }.build()
    val Appearance = MiuixIcons.Theme
    val Material = MiuixIcons.Layers
    val Transparency = MiuixIcons.Hide
    val Motion = MiuixIcons.Play
    val Haptic = MiuixIcons.Phone
    val Tune = MiuixIcons.Tune
    val Sponsor = MiuixIcons.FavoritesFill
    val Hint = MiuixIcons.Info
    val Reset = MiuixIcons.Reset
    val Retry = MiuixIcons.Refresh
    val Download = MiuixIcons.Download
    val Upload = MiuixIcons.UploadCloud
    val Timer = MiuixIcons.Timer
    val Filter = MiuixIcons.Filter
    val Search = MiuixIcons.Search
    val Rename = MiuixIcons.Rename
    val Copy = MiuixIcons.Copy
    val Lock = MiuixIcons.Lock
    val Backup = MiuixIcons.Backup
    val Media = MiuixIcons.Play
}
