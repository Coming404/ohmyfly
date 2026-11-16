package com.ohmyfly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Clock

@Composable
internal fun CatalogAction(text: String, icon: ImageVector, modifier: Modifier = Modifier,
    enabled: Boolean = true, destructive: Boolean = false,
    contentColor: Color = if (destructive) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
    expanded: Boolean? = null,
    onClick: () -> Unit) {
    Button(onClick = hapticClick(onClick), modifier = modifier, enabled = enabled,
        minWidth = 0.dp, minHeight = 44.dp, insideMargin = PaddingValues(horizontal = 4.dp),
        colors = ButtonDefaults.buttonColors(color = Color.Transparent, disabledColor = Color.Transparent,
            contentColor = contentColor, disabledContentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary)) {
        Icon(icon, null, Modifier.size(19.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, modifier = Modifier.weight(1f, fill = false), autoSize = CatalogTextSizing.CompactButton,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        expanded?.let {
            Spacer(Modifier.width(4.dp))
            CatalogExpansionArrow(it, if (enabled) contentColor else MiuixTheme.colorScheme.disabledOnSurface)
        }
    }
}

// 展开图标单独布局，避免文本字符基线影响垂直居中。
@Composable
internal fun CatalogExpansionArrow(expanded: Boolean, color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary) {
    Icon(MiuixIcons.Basic.ArrowRight, if (expanded) "收起" else "展开",
        Modifier.size(16.dp).rotate(if (expanded) -90f else 90f), tint = color)
}

@Composable
internal fun CatalogTile(icon: ImageVector, size: Int = 40, color: Color = MiuixTheme.colorScheme.primary) {
    Box(Modifier.size(size.dp).background(color.copy(alpha = 0.1f),
        RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * 0.56f).dp), tint = color)
    }
}

@Composable
internal fun CatalogBadge(text: String, failed: Boolean = false) {
    val color = if (failed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
    Text(text, modifier = Modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
        .padding(horizontal = 9.dp, vertical = 4.dp), color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
internal fun CatalogEmpty(
    title: String,
    hint: String = "",
    onAction: (() -> Unit)? = null,
    actionLabel: String = "添加塔台",
    icon: ImageVector = DriftIcons.Nodes,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CatalogTile(icon, 48)
        Text(title, fontSize = 16.sp)
        if (hint.isNotBlank()) Text(hint, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            autoSize = CatalogTextSizing.Notice)
        onAction?.let { CatalogTextButton(actionLabel, onClick = hapticClick(it),
            colors = ButtonDefaults.buttonColorsPrimary()) }
    }
}

internal fun catalogAge(value: Long?): String {
    if (value == null) return "尚未同步"
    val minutes = (Clock.System.now().toEpochMilliseconds() - value).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        minutes < 1440 -> "${minutes / 60} 小时前"
        else -> "${minutes / 1440} 天前"
    }
}
