package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.MaterialMode
import com.ohmyfly.ui.DriftIcons
import com.ohmyfly.ui.ThemeMode
import top.yukonga.miuix.kmp.basic.Icon
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
        item(key = "appearance-title") { SettingsSectionTitle("外观", SettingsIcons.Appearance) }
        item(key = "appearance") {
            SettingsCard {
                ChoiceSetting("明暗模式", settings.themeMode.name, ThemeMode.entries.map { it.name to it.label }, SettingsIcons.Appearance) { name ->
                    onChange { it.copy(themeMode = ThemeMode.valueOf(name)) }
                }
                ChoiceSetting("界面材质", settings.materialMode.name, MaterialMode.entries.map { it.name to it.label }, SettingsIcons.Material) { name ->
                    onChange { it.copy(materialMode = MaterialMode.valueOf(name)) }
                }
                ToggleSetting("减少透明效果", value = settings.reduceTransparency, leadingIcon = SettingsIcons.Transparency,
                    onChange = { enabled -> onChange { it.copy(reduceTransparency = enabled) } })
                ToggleSetting("减少动态效果", value = settings.reduceMotion, leadingIcon = SettingsIcons.Motion,
                    onChange = { enabled -> onChange { it.copy(reduceMotion = enabled) } })
                HapticSetting(settings, onChange)
            }
        }
        item(key = "tests-title") { SettingsSectionTitle("测试", DriftIcons.Speed) }
        item(key = "tests") {
            SettingsCard {
                SettingsActionRow(SettingsPage.Speed.title, DriftIcons.Speed, onClick = { onPage(SettingsPage.Speed) })
                SettingsActionRow(SettingsPage.Media.title, SettingsIcons.Media, onClick = { onPage(SettingsPage.Media) })
            }
        }
        item(key = "network-title") { SettingsSectionTitle("塔台与网络", DriftIcons.Subscriptions) }
        item(key = "network") {
            SettingsCard {
                SettingsActionRow(SettingsPage.Subscription.title, DriftIcons.Subscriptions, onClick = { onPage(SettingsPage.Subscription) })
                SettingsActionRow(SettingsPage.Mirrors.title, MirrorIcons.Github,
                    value = if (!settings.mirrorEnabled) "已关闭" else
                        settings.mirrors.firstOrNull { it.id == settings.selectedMirrorId }?.name
                            ?: settings.mirrors.firstOrNull()?.name ?: "暂无镜像源",
                    onClick = { onPage(SettingsPage.Mirrors) })
            }
        }
        item(key = "app-title") { SettingsSectionTitle("应用", DriftIcons.Settings) }
        item(key = "app") {
            SettingsCard {
                SettingsActionRow(SettingsPage.Privacy.title, SettingsIcons.Lock, onClick = { onPage(SettingsPage.Privacy) })
                SettingsActionRow(SettingsPage.About.title, SettingsIcons.Hint, onClick = { onPage(SettingsPage.About) })
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
        Icon(DriftIcons.Wave, contentDescription = null, modifier = Modifier.size(32.dp),
            tint = MiuixTheme.colorScheme.primary)
        Text("OhMyFly", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f))
        Text(version, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
    }
}
