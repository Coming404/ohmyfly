package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.HapticLevel
import com.ohmyfly.ui.LocalAppHaptics
import com.ohmyfly.ui.hapticClick
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HapticSetting(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var level by remember(settings.hapticLevel) { mutableStateOf(settings.hapticLevel) }
    val feedback = LocalAppHaptics.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BasicComponent(
        title = "点击震动",
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = Modifier.semantics { stateDescription = if (expanded) "已展开" else "已收起" },
        onClick = hapticClick { expanded = !expanded },
        onClickLabel = if (expanded) "收起" else "展开",
        role = Role.Button,
        endActions = {
            Text(if (settings.hapticEnabled) level.label else "已关闭", fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(end = 8.dp))
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(width = 10.dp, height = 16.dp).graphicsLayer {
                    rotationZ = if (expanded) 90f else if (rtl) 180f else 0f
                },
            )
        },
    )
    if (expanded) {
        ToggleSetting("启用震动", value = settings.hapticEnabled,
            onChange = { enabled -> onChange { it.copy(hapticEnabled = enabled) } })
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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
