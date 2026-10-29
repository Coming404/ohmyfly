package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsLeadingIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp),
        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
internal fun SettingsName(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Normal, color = MiuixTheme.colorScheme.onSurface)
}

@Composable
internal fun SettingsSectionTitle(text: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)
        .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onSurface)
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun SettingsPageIntro(title: String, icon: ImageVector, hint: String = "") {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
            Icon(icon, null, Modifier.size(24.dp), tint = MiuixTheme.colorScheme.primary)
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface)
        }
        if (hint.isNotBlank()) SettingsHint(hint)
    }
}

@Composable
internal fun SettingsHint(text: String, isError: Boolean = false) {
    val color = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        if (isError) liveRegion = LiveRegionMode.Polite
    }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(if (isError) MirrorIcons.Alert else SettingsIcons.Hint, null, Modifier.size(16.dp), tint = color)
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Normal, color = color, modifier = Modifier.weight(1f))
    }
}

/** A read-only row has no click action, role or trailing navigation arrow. */
@Composable
internal fun SettingsInfo(
    title: String,
    summary: String,
    icon: ImageVector,
    value: String? = null,
    isError: Boolean = false,
) {
    BasicComponent(startAction = { SettingsLeadingIcon(icon) },
        endActions = {
            if (value != null) Text(value, fontSize = 14.sp, textAlign = TextAlign.End,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f, fill = false))
        },
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        SettingsName(title)
        if (summary.isNotBlank()) SettingsHint(summary, isError = isError)
    }
}

@Composable
internal fun SettingsButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = if (destructive) ButtonDefaults.buttonColors(
        color = MiuixTheme.colorScheme.error, contentColor = MiuixTheme.colorScheme.onError,
    ) else ButtonDefaults.buttonColorsPrimary()
    Button(onClick = com.ohmyfly.ui.hapticClick(onClick), modifier = modifier,
        enabled = enabled, colors = colors, minHeight = 48.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Normal)
        }
    }
}
