package dev.ohmydrift.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.ohmydrift.settings.AppSettings
import dev.ohmydrift.ui.settings.*
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
internal fun SettingsScreen(
    listState: LazyListState,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    page: SettingsPage,
    onPage: (SettingsPage) -> Unit,
    version: String,
    writeError: String?,
    onDismissError: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val direction = LocalLayoutDirection.current
    val pagePadding = if (writeError == null) contentPadding else PaddingValues(
        start = contentPadding.calculateStartPadding(direction),
        end = contentPadding.calculateEndPadding(direction),
        bottom = contentPadding.calculateBottomPadding(),
    )
    Column(Modifier.fillMaxSize().padding(top = if (writeError != null) contentPadding.calculateTopPadding() else 0.dp)) {
        if (writeError != null) {
            BasicComponent(title = "设置未保存", summary = writeError)
            TextButton(text = "关闭提示", onClick = onDismissError)
        }
        key(page) {
            when (page) {
                SettingsPage.Home -> SettingsHome(settings, onPage, listState, pagePadding)
                SettingsPage.Speed -> SpeedSettingsPage(settings, onChange, pagePadding)
                SettingsPage.Media -> MediaSettingsPage(settings, onChange, pagePadding)
                SettingsPage.Subscription -> SubscriptionSettingsPage(settings, onChange, pagePadding)
                SettingsPage.Mirrors -> MirrorSettingsPage(settings, onChange, pagePadding)
                SettingsPage.Appearance -> AppearanceSettingsPage(
                    listState = rememberLazyListState(), themeMode = settings.themeMode,
                    reduceTransparency = settings.reduceTransparency,
                    onThemeMode = { mode -> onChange { it.copy(themeMode = mode) } },
                    onReduceTransparency = { enabled -> onChange { it.copy(reduceTransparency = enabled) } },
                    contentPadding = pagePadding,
                )
                SettingsPage.Privacy -> PrivacySettingsPage(settings, onChange, pagePadding)
                SettingsPage.About -> AboutSettingsPage(version, pagePadding)
            }
        }
    }
}

@Composable
private fun SettingsHome(settings: AppSettings, onPage: (SettingsPage) -> Unit, state: LazyListState, padding: PaddingValues) {
    SettingsList(padding, state) {
        item { SmallTitle("测试") }
        item { SettingsCard {
            SettingsEntry(SettingsPage.Speed, "延迟并发 ${settings.speed.latencyConcurrency} · 单节点连接 ${settings.speed.connectionsPerNode}", onPage)
            SettingsEntry(SettingsPage.Media, "${settings.media.platforms.size} 项 · 节点并发 ${settings.media.concurrency}", onPage)
        } }
        item { SmallTitle("订阅与网络") }
        item { SettingsCard {
            SettingsEntry(SettingsPage.Subscription, "请求策略 · 名称与协议过滤", onPage)
            SettingsEntry(SettingsPage.Mirrors,
                "${settings.mirrors.firstOrNull { it.id == settings.selectedMirrorId }?.name ?: "直连"} · 失败后按手动顺序降级", onPage)
        } }
        item { SmallTitle("应用") }
        item { SettingsCard {
            SettingsEntry(SettingsPage.Appearance, settings.themeMode.label, onPage)
            SettingsEntry(SettingsPage.Privacy, "本地存储 · 恢复默认", onPage)
            SettingsEntry(SettingsPage.About, "版本 · 开源许可与来源", onPage)
        } }
    }
}

@Composable
private fun SettingsEntry(page: SettingsPage, summary: String, onPage: (SettingsPage) -> Unit) {
    BasicComponent(title = "${page.title}  ›", summary = summary, onClick = { onPage(page) }, role = Role.Button)
}
