package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import com.ohmyfly.ui.DriftIcons

@Composable
internal fun AboutSettingsPage(version: String, padding: PaddingValues) {
    val uri = LocalUriHandler.current
    // 页面显示短提交号，日志保留完整标识用于对照构建。
    val coreVersion = androidx.compose.runtime.remember { com.ohmyfly.nativecore.nativeCoreVersion().take(12) }
    SettingsList(padding) {
        item { SettingsPageIntro("关于 OhMyFly") }
        item { SettingsCard {
            SettingsInfo("App 版本", "", SettingsIcons.Hint, value = version)
            SettingsInfo("NativeCore", "", SettingsIcons.Tune, value = coreVersion)
            SettingsActionRow("项目主页", MirrorIcons.Github, "Coming404/ohmyfly") {
                uri.openUri("https://github.com/Coming404/ohmyfly")
            }
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
