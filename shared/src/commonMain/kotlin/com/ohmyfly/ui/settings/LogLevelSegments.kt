// 日志级别使用与预览一致的并排分段按钮，持久化仍由设置会话确认。
package com.ohmyfly.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.logging.LogLevel
import com.ohmyfly.ui.LocalAppSurfaceColors
import com.ohmyfly.ui.hapticChange
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LogLevelSetting(value: LogLevel, onChange: (LogLevel) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(SettingsIcons.Filter, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text("记录级别", fontSize = 15.sp, modifier = Modifier.weight(1f))
        LogLevelSegments(value, "记录级别", onChange)
    }
}

@Composable
internal fun LogLevelSegments(value: LogLevel, label: String, onChange: (LogLevel) -> Unit) {
    val surfaces = LocalAppSurfaceColors.current
    val change = hapticChange(onChange)
    Row(Modifier.clip(RoundedCornerShape(13.dp)).background(surfaces.page).padding(3.dp)
        .selectableGroup().semantics { contentDescription = label }) {
        LogLevel.entries.forEach { level ->
            val selected = level == value
            val shape = RoundedCornerShape(10.dp)
            Box(Modifier.defaultMinSize(minWidth = 57.dp, minHeight = 44.dp)
                .then(if (selected) Modifier.shadow(1.dp, shape).background(surfaces.card, shape) else Modifier)
                .clip(shape).selectable(selected, role = Role.RadioButton,
                    onClick = { if (!selected) change(level) }).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center) {
                Text(level.label, fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) surfaces.onCard else MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}
