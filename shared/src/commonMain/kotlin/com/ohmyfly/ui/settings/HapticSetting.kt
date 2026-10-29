package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.HapticLevel
import com.ohmyfly.ui.LocalAppHaptics
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun HapticSetting(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var level by remember(settings.hapticLevel) { mutableStateOf(settings.hapticLevel) }
    val feedback = LocalAppHaptics.current
    SettingsExpandableRow("点击震动", if (settings.hapticEnabled) level.label else "已关闭",
        SettingsIcons.Haptic, expanded, onClick = { expanded = !expanded })
    SettingsExpansion(expanded) {
        ToggleSetting("启用震动", value = settings.hapticEnabled, leadingIcon = SettingsIcons.Haptic,
            onChange = { enabled -> onChange { it.copy(hapticEnabled = enabled) } })
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsLeadingIcon(SettingsIcons.Tune)
                SettingsName("震动强度")
            }
            Slider(
                value = level.ordinal.toFloat(),
                onValueChange = { value ->
                    val next = HapticLevel.entries[value.roundToInt().coerceIn(0, 2)]
                    if (next != level) {
                        level = next
                        onChange { it.copy(hapticLevel = next) }
                        feedback.preview(next)
                    }
                },
                enabled = settings.hapticEnabled,
                valueRange = 0f..2f,
                steps = 1,
                hapticEffect = SliderDefaults.SliderHapticEffect.None,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "震动强度"
                    stateDescription = level.label
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HapticLevel.entries.forEach { Text(it.label, fontSize = 14.sp) }
            }
        }
    }
}
