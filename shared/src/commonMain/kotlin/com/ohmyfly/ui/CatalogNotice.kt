// 塔台与航班的就地反馈胶囊；长信息可换行，容器不固定高度。
package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

// 多行反馈使用明确的红、琥珀、绿语义配色，浅深色均保留可见对比。
@Composable
internal fun CatalogNotice(text: String, kind: CatalogNoticeKind = CatalogNoticeKind.Error) {
    val scheme = MiuixTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val foreground = when (kind) {
        CatalogNoticeKind.Error -> if (dark) Color(0xFFFF8A8A) else Color(0xFFC62828)
        CatalogNoticeKind.Warning -> if (dark) Color(0xFFFFD54F) else Color(0xFF8A5A00)
        CatalogNoticeKind.Success -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
    }
    val background = when (kind) {
        CatalogNoticeKind.Error -> if (dark) Color(0xFF54272B) else Color(0xFFFFE2E2)
        else -> foreground.copy(alpha = 0.12f)
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        .background(background, AppCorners.shape(AppCorners.Card)).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (kind == CatalogNoticeKind.Success) DriftIcons.Check else MirrorIcons.Alert,
            contentDescription = when (kind) {
                CatalogNoticeKind.Error -> "错误"
                CatalogNoticeKind.Warning -> "提示"
                CatalogNoticeKind.Success -> "成功"
            }, modifier = Modifier.size(18.dp), tint = foreground)
        Text(text, modifier = Modifier.weight(1f), color = foreground, autoSize = CatalogTextSizing.Notice)
    }
}
