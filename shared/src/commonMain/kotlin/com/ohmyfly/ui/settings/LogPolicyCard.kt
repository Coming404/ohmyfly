// 日志设置复用现有自动保存表单；只提供信息、详细两个级别。
package com.ohmyfly.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.DayMillis
import com.ohmyfly.settings.MiB

@Composable
internal fun LogPolicyCard(settings: AppSettings, onChange: (String, (AppSettings) -> AppSettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    SettingsCard {
        LogLevelSetting(settings.logging.level) { value ->
            onChange("日志记录级别") { it.copy(logging = it.logging.copy(level = value)) }
        }
        SettingsExpandableRow("存储设置",
            "${settings.logging.fileMiB} / ${settings.logging.totalMiB} MiB  ${settings.logging.retentionDays} 天",
            SettingsIcons.Tune, expanded) { expanded = !expanded }
        SettingsExpansion(expanded) {
            LogStorageFields()
        }
    }
}

@Composable
private fun LogStorageFields() {
    TextSetting("单文件大小（MiB）", { it.logging.fileMiB.toString() }, SettingsIcons.Backup,
        keyboard = KeyboardType.Number, validate = { logNumberError(it, Long.MAX_VALUE / MiB) },
        write = { state, value -> state.copy(logging = state.logging.copy(fileMiB = value.toLong())) })
    TextSetting("总文件大小（MiB）", { it.logging.totalMiB.toString() }, SettingsIcons.Backup,
        keyboard = KeyboardType.Number, validate = { logNumberError(it, Long.MAX_VALUE / MiB) },
        write = { state, value -> state.copy(logging = state.logging.copy(totalMiB = value.toLong())) })
    TextSetting("保留时间（天）", { it.logging.retentionDays.toString() }, SettingsIcons.Timer,
        keyboard = KeyboardType.Number, validate = { logNumberError(it, Long.MAX_VALUE / DayMillis) },
        write = { state, value -> state.copy(logging = state.logging.copy(retentionDays = value.toLong())) })
}

private fun logNumberError(value: String, max: Long): String? =
    if (value.toLongOrNull()?.let { it in 1..max } == true) null else "请输入有效的正整数"
