package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.ui.LocalAppSurfaceColors
import com.ohmyfly.ui.hapticChange
import com.ohmyfly.ui.withPageSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.squircle.squircleBorder
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
    leadingIcon: ImageVector,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    SettingsRowFeedback {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .toggleable(value, enabled = enabled, role = Role.Switch, onValueChange = hapticChange(onChange)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            // Matches the 8dp gap BasicComponent puts between startAction and content.
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsLeadingIcon(leadingIcon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsName(title)
                if (summary.isNotBlank()) SettingsHint(summary)
            }
            Switch(checked = value, enabled = enabled, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
internal fun TextSetting(
    title: String,
    read: (AppSettings) -> String,
    leadingIcon: ImageVector,
    hint: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    validate: (String) -> String? = { null },
    write: (AppSettings, String) -> AppSettings,
) {
    val edits = LocalSettingsEdits.current
    val draft = edits.draft(LocalSettingsPage.current, title, read, validate, write)
    val focus = LocalFocusManager.current
    SettingsExpandableRow(title, read(edits.session.state.confirmedSettings ?: edits.session.state.settings).ifBlank { "未设置" },
        leadingIcon, draft.expanded) {
        if (draft.expanded) {
            edits.finish(draft, SettingFinish.Collapse)
            focus.clearFocus()
        } else draft.open()
    }
    SettingsExpansion(draft.expanded) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsInputMenu {
                TextField(
                    value = draft.value,
                    onValueChange = { edits.input(draft, it) },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { edits.focus(draft, it.isFocused) }
                        .semantics { contentDescription = title; draft.error?.let { error(it) } },
                    readOnly = draft.saving || edits.session.state.readFailed,
                    singleLine = true,
                    textStyle = MiuixTheme.textStyles.main.copy(fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        edits.finish(draft, SettingFinish.Done)
                        focus.clearFocus()
                    }),
                )
            }
            if (hint.isNotEmpty()) SettingsHint(hint)
            if (draft.saving) SettingsHint("正在保存…")
            draft.error?.let { SettingsHint(it, isError = true) }
            if (draft.error != null && validate(draft.value.text) == null && !edits.session.state.readFailed) {
                SettingsButton("重试", SettingsIcons.Retry, enabled = !draft.saving) { edits.finish(draft, SettingFinish.Retry) }
            }
        }
    }
}

@Composable
internal fun NumberSetting(
    title: String,
    read: (AppSettings) -> Int,
    range: IntRange,
    leadingIcon: ImageVector,
    hint: String = "",
    write: (AppSettings, Int) -> AppSettings,
) {
    TextSetting(title, { read(it).toString() }, leadingIcon, hint, KeyboardType.Number,
        validate = { validateIntegerInput(it, range) },
        write = { current, text -> write(current, text.toInt()) })
}

@Composable
internal fun DecimalSetting(
    title: String,
    read: (AppSettings) -> Double,
    leadingIcon: ImageVector,
    hint: String,
    write: (AppSettings, Double) -> AppSettings,
) {
    TextSetting(title, { read(it).toString() }, leadingIcon, hint, KeyboardType.Decimal,
        validate = ::validateDecimalInput,
        write = { current, text -> write(current, text.toDouble()) })
}

internal fun validateIntegerInput(text: String, range: IntRange): String? =
    if (text.toIntOrNull()?.let { it in range } == true) null else "请输入 ${range.first}–${range.last} 的整数"

internal fun validateDecimalInput(text: String): String? =
    if (text.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..10000.0 } == true) null else "请输入 0–10000 的有效数值"
