package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ohmyfly.ui.LocalAppSurfaceColors
import com.ohmyfly.ui.hapticChange
import com.ohmyfly.ui.hapticClick
import com.ohmyfly.ui.withPageSpacing
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsList(
    padding: PaddingValues,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        state = state,
        contentPadding = padding.withPageSpacing(LocalLayoutDirection.current, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val surfaces = LocalAppSurfaceColors.current
    Card(
        modifier = Modifier.fillMaxWidth().squircleBorder(
            width = 0.5.dp,
            color = surfaces.cardOutline,
            cornerRadius = CardDefaults.CornerRadius,
        ),
        colors = CardDefaults.defaultColors(color = surfaces.card, contentColor = surfaces.onCard),
        content = content,
    )
}

@Composable
internal fun ToggleSetting(
    title: String,
    summary: String = "",
    value: Boolean,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value, enabled = enabled, role = Role.Switch, onValueChange = hapticChange(onChange)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp)
            if (summary.isNotBlank()) Text(summary, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Switch(checked = value, enabled = enabled, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
internal fun TextSetting(
    title: String,
    value: String,
    hint: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    validate: (String) -> String? = { null },
    onSave: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(value, editing) { mutableStateOf(TextFieldValue(value)) }
    val input = draft.text
    val validationError = validate(input)
    LaunchedEffect(value) { editing = false }
    BasicComponent(title = title, summary = value.ifBlank { "未设置" }, onClick = hapticClick { editing = !editing }, role = Role.Button)
    if (editing) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsInputMenu {
                TextField(value = draft, onValueChange = { draft = it }, label = title, modifier = Modifier.fillMaxWidth().semantics {
                    validationError?.let { error(it) }
                },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false))
            }
            if (hint.isNotEmpty()) Text(hint, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (validationError != null) Text(validationError, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(text = "保存", enabled = validationError == null && input != value, onClick = hapticClick { onSave(input) })
                TextButton(text = "取消", onClick = hapticClick { editing = false })
            }
        }
    }
}

@Composable
internal fun NumberSetting(title: String, value: Int, range: IntRange, hint: String = "", onSave: (Int) -> Unit) {
    TextSetting(title, value.toString(), hint = hint, keyboard = KeyboardType.Number,
        validate = { if (it.toIntOrNull()?.let { number -> number in range } == true) null else "请输入 ${range.first}–${range.last} 的整数" },
        onSave = { onSave(it.toInt()) })
}

@Composable
internal fun DecimalSetting(title: String, value: Double, hint: String, onSave: (Double) -> Unit) {
    TextSetting(title, value.toString(), hint = hint, keyboard = KeyboardType.Decimal,
        validate = { text -> if (text.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..10000.0 } == true) null else "请输入 0–10000 的有效数值" },
        onSave = { onSave(it.toDouble()) })
}

@Composable
internal fun ResetSettings(title: String = "恢复本页默认", onReset: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    if (confirm) {
        SettingsCard {
            BasicComponent(title = "确认恢复默认？", summary = "仅重置设置，不删除塔台、航班、自定义镜像或历史。")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(text = "恢复默认", onClick = hapticClick { onReset(); confirm = false })
                TextButton(text = "取消", onClick = hapticClick { confirm = false })
            }
        }
    } else TextButton(text = title, modifier = Modifier.fillMaxWidth(), onClick = hapticClick { confirm = true })
}
