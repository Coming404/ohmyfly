package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import com.ohmyfly.ui.DriftIcons
import com.ohmyfly.startup.rememberExportStartupRecords

@Composable
internal fun AboutSettingsPage(version: String, padding: PaddingValues) {
    val uri = LocalUriHandler.current
    val exportStartupRecords = rememberExportStartupRecords()
    SettingsList(padding) {
        item { SettingsPageIntro("关于 OhMyFly") }
        item { SettingsCard {
            SettingsInfo("版本", "", SettingsIcons.Hint, value = version)
            SettingsActionRow("导出启动记录", SettingsIcons.Backup) { exportStartupRecords() }
            SettingsActionRow("项目主页", MirrorIcons.Github, "Coming404/ohmyfly") {
                uri.openUri("https://github.com/Coming404/ohmyfly")
            }
            SettingsInfo("航班与媒体核心", "尚未接入；设置保存不代表测试已经可用。", SettingsIcons.Tune)
        } }
        item { SettingsSectionTitle("开源许可与来源", SettingsIcons.Copy) }
        item { SettingsCard {
            SettingsActionRow("Kotlin / Compose Multiplatform", SettingsIcons.Copy, "Apache-2.0") {
                uri.openUri("https://github.com/JetBrains/compose-multiplatform/blob/master/LICENSE.txt")
            }
            SettingsActionRow("Miuix 0.9.4", SettingsIcons.Material, "Apache-2.0") {
                uri.openUri("https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/LICENSE")
            }
            SettingsActionRow("AndroidX DataStore", SettingsIcons.Backup, "Apache-2.0") {
                uri.openUri("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
            SettingsActionRow("kotlinx.coroutines / Okio", SettingsIcons.Copy, "Apache-2.0") {
                uri.openUri("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        } }
        item { SettingsSectionTitle("设计参考", SettingsIcons.Hint) }
        item { SettingsCard {
            SettingsActionRow("clash-speedtest", DriftIcons.Speed, "faceair · GPL-3.0") {
                uri.openUri("https://github.com/faceair/clash-speedtest/tree/1e1fb9e2bbb29282cce5613a6c6b946b85169fdc")
            }
            SettingsActionRow("subs-check-pro", DriftIcons.Subscriptions, "sinspired · GPL-3.0") {
                uri.openUri("https://github.com/sinspired/subs-check-pro/tree/e53554f9ad2cc9770f483f0c08b7d9ea911c331d")
            }
        } }
    }
}
