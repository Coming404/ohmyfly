// 塔台与航班的就地反馈胶囊；长信息可换行，容器不固定高度。
package com.ohmyfly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ohmyfly.ui.settings.MirrorIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class CatalogNoticeKind { Error, Warning, Success }

// Miuix没有多行提示胶囊组件，错误使用官方配色，警告与成功使用应用语义配色。
@Composable
internal fun CatalogNotice(text: String, kind: CatalogNoticeKind = CatalogNoticeKind.Error) {
    val scheme = MiuixTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val foreground = when (kind) {
        CatalogNoticeKind.Error -> scheme.onErrorContainer
        CatalogNoticeKind.Warning -> if (dark) Color(0xFFFFD54F) else Color(0xFF8A5A00)
        CatalogNoticeKind.Success -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
    }
    val background = if (kind == CatalogNoticeKind.Error) scheme.errorContainer else foreground.copy(alpha = 0.12f)
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        .background(background, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(if (kind == CatalogNoticeKind.Success) DriftIcons.Check else MirrorIcons.Alert,
            contentDescription = when (kind) {
                CatalogNoticeKind.Error -> "错误"
                CatalogNoticeKind.Warning -> "提示"
                CatalogNoticeKind.Success -> "成功"
            }, modifier = Modifier.padding(top = 2.dp).size(18.dp), tint = foreground)
        Text(text, modifier = Modifier.weight(1f), color = foreground, autoSize = CatalogTextSizing.Notice)
    }
}
