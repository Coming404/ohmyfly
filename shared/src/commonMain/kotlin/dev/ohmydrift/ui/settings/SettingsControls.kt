package dev.ohmydrift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.ohmydrift.ui.ChoiceOption
import dev.ohmydrift.ui.withPageSpacing
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
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
        contentPadding = padding.withPageSpacing(LocalLayoutDirection.current, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable
internal fun ToggleSetting(title: String, summary: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value, role = Role.Switch, onValueChange = onChange).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MiuixTheme.textStyles.title4)
            Text(summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Switch(checked = value, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
internal fun ChoiceSetting(title: String, value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    BasicComponent(title = title, summary = options.firstOrNull { it.first == value }?.second ?: value,
        onClick = { expanded = !expanded }, role = Role.Button)
    if (expanded) {
        Column(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 16.dp)) {
            options.forEach { (id, label) ->
                ChoiceOption(label, value == id, onClick = { onChange(id); expanded = false }, modifier = Modifier.fillMaxWidth())
            }
        }
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
    val draft = remember(value, editing) { TextFieldState(value) }
    val input = draft.text.toString()
    val error = validate(input)
    LaunchedEffect(value) { editing = false }
    BasicComponent(title = title, summary = value.ifBlank { "未设置" }, onClick = { editing = !editing }, role = Role.Button)
    if (editing) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(state = draft, label = title, modifier = Modifier.fillMaxWidth(),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false))
            if (hint.isNotEmpty()) Text(hint, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (error != null) Text(error)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "保存", enabled = error == null && input != value, onClick = { onSave(input) })
                TextButton(text = "取消", onClick = { editing = false })
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
            BasicComponent(title = "确认恢复默认？", summary = "仅重置设置，不删除订阅、节点、自定义镜像或历史。")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "恢复默认", onClick = { onReset(); confirm = false })
                TextButton(text = "取消", onClick = { confirm = false })
            }
        }
    } else TextButton(text = title, modifier = Modifier.fillMaxWidth(), onClick = { confirm = true })
}
