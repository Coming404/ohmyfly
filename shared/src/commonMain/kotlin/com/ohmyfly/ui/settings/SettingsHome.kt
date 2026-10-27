package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.MaterialMode
import com.ohmyfly.ui.DriftIcons
import com.ohmyfly.ui.ThemeMode
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsHome(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onPage: (SettingsPage) -> Unit,
    version: String,
    state: LazyListState,
    padding: PaddingValues,
) {
    SettingsList(padding, state) {
        item(key = "identity") { SettingsIdentity(version) }
        item(key = "appearance-title") { SmallTitle("外观") }
        item(key = "appearance") {
            SettingsCard {
                ChoiceSetting("明暗模式", settings.themeMode.name, ThemeMode.entries.map { it.name to it.label }) { name ->
                    onChange { it.copy(themeMode = ThemeMode.valueOf(name)) }
                }
                ChoiceSetting("界面材质", settings.materialMode.name, MaterialMode.entries.map { it.name to it.label }) { name ->
                    onChange { it.copy(materialMode = MaterialMode.valueOf(name)) }
                }
                ToggleSetting("减少透明效果", value = settings.reduceTransparency,
                    onChange = { enabled -> onChange { it.copy(reduceTransparency = enabled) } })
                ToggleSetting("减少动态效果", value = settings.reduceMotion,
                    onChange = { enabled -> onChange { it.copy(reduceMotion = enabled) } })
                HapticSetting(settings, onChange)
            }
        }
        item(key = "tests-title") { SmallTitle("测试") }
        item(key = "tests") {
            SettingsCard {
                SettingsActionRow(SettingsPage.Speed.title, onClick = { onPage(SettingsPage.Speed) })
                SettingsActionRow(SettingsPage.Media.title, onClick = { onPage(SettingsPage.Media) })
            }
        }
        item(key = "network-title") { SmallTitle("塔台与网络") }
        item(key = "network") {
            SettingsCard {
                SettingsActionRow(SettingsPage.Subscription.title, onClick = { onPage(SettingsPage.Subscription) })
                SettingsActionRow(SettingsPage.Mirrors.title,
                    value = if (!settings.mirrorEnabled) "已关闭" else
                        settings.mirrors.firstOrNull { it.id == settings.selectedMirrorId }?.name
                            ?: settings.mirrors.firstOrNull()?.name ?: "暂无镜像源",
                    onClick = { onPage(SettingsPage.Mirrors) })
            }
        }
        item(key = "app-title") { SmallTitle("应用") }
        item(key = "app") {
            SettingsCard {
                SettingsActionRow(SettingsPage.Privacy.title, onClick = { onPage(SettingsPage.Privacy) })
                SettingsActionRow(SettingsPage.About.title, onClick = { onPage(SettingsPage.About) })
            }
        }
    }
}

@Composable
private fun SettingsIdentity(version: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(DriftIcons.Wave, contentDescription = null, modifier = Modifier.size(40.dp),
            tint = MiuixTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("OhMyFly", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("塔台管理与航班起飞", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(version, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
