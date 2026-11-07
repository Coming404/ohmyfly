// 设置页标题属于列表内容，随滚动离开屏幕，不覆盖品牌卡片。
package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.MaterialMode
import com.ohmyfly.ui.DriftIcons
import com.ohmyfly.ui.ThemeMode
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsHome(
    settings: AppSettings,
    onChange: (String, (AppSettings) -> AppSettings) -> Unit,
    onPage: (SettingsPage) -> Unit,
    version: String,
    state: LazyListState,
    padding: PaddingValues,
) {
    SettingsList(padding, state) {
        item(key = "title") {
            Box(Modifier.fillMaxWidth().heightIn(min = 36.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    SettingsPage.Home.title,
                    modifier = Modifier.semantics { heading() },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item(key = "identity") { SettingsBoardingPass(version, onSponsor = { onPage(SettingsPage.Sponsor) }) }
        item(key = "appearance-title") { SettingsSectionTitle("外观", SettingsIcons.Appearance) }
        item(key = "appearance") {
            SettingsCard {
                ChoiceSetting("明暗模式", settings.themeMode.name, ThemeMode.entries.map { it.name to it.label }, SettingsIcons.Appearance) { name ->
                    onChange("明暗模式") { it.copy(themeMode = ThemeMode.valueOf(name)) }
                }
                ChoiceSetting("界面材质", settings.materialMode.name, MaterialMode.entries.map { it.name to it.label }, SettingsIcons.Material) { name ->
                    onChange("界面材质") { it.copy(materialMode = MaterialMode.valueOf(name)) }
                }
                ToggleSetting("减少透明效果", value = settings.reduceTransparency, leadingIcon = SettingsIcons.Transparency,
                    onChange = { enabled -> onChange("减少透明效果") { it.copy(reduceTransparency = enabled) } })
                ToggleSetting("减少动态效果", value = settings.reduceMotion, leadingIcon = SettingsIcons.Motion,
                    onChange = { enabled -> onChange("减少动态效果") { it.copy(reduceMotion = enabled) } })
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
                SettingsActionRow(SettingsPage.Logs.title, SettingsIcons.Backup, onClick = { onPage(SettingsPage.Logs) })
                SettingsActionRow(SettingsPage.Privacy.title, SettingsIcons.Lock, onClick = { onPage(SettingsPage.Privacy) })
                SettingsActionRow(SettingsPage.About.title, SettingsIcons.Hint, onClick = { onPage(SettingsPage.About) })
            }
        }
    }
}
