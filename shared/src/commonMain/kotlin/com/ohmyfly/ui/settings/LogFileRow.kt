// 日志文件行独立控制字号与截断，不改变其他设置行的默认排版。
package com.ohmyfly.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.logging.LogFileInfo
import com.ohmyfly.ui.appIconTint
import com.ohmyfly.ui.hapticClick
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LogFileRow(file: LogFileInfo, name: String, expanded: Boolean, onClick: () -> Unit) {
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
        .clickable(role = Role.Button, onClickLabel = if (expanded) "收起日志文件" else "展开日志文件",
            onClick = hapticClick(onClick)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(SettingsIcons.File, null, Modifier.size(20.dp), tint = appIconTint())
        Text(name, Modifier.weight(1f), fontSize = 15.sp, lineHeight = 22.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(logBytes(file.bytes), fontSize = 12.sp, fontWeight = FontWeight.Normal,
            maxLines = 1, color = muted)
        SettingsExpansionArrow(expanded)
    }
}
