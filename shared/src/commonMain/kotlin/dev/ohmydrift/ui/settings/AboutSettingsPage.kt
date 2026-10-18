package dev.ohmydrift.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun AboutSettingsPage(version: String, padding: PaddingValues) {
    val uri = LocalUriHandler.current
    SettingsList(padding) {
        item { SettingsCard {
            BasicComponent(title = "ohmydrift", summary = version)
            BasicComponent(title = "项目主页", summary = "Coming404/ohmydrift", role = Role.Button,
                onClick = { uri.openUri("https://github.com/Coming404/ohmydrift") })
            BasicComponent(title = "节点与媒体核心", summary = "尚未接入；设置保存不代表节点测速或平台检测已经可用。")
        } }
        item { SmallTitle("开源许可与来源") }
        item { SettingsCard {
            BasicComponent(title = "Kotlin / Compose Multiplatform", summary = "Apache-2.0", role = Role.Button,
                onClick = { uri.openUri("https://github.com/JetBrains/compose-multiplatform/blob/master/LICENSE.txt") })
            BasicComponent(title = "Miuix 0.9.4", summary = "Apache-2.0", role = Role.Button,
                onClick = { uri.openUri("https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/LICENSE") })
            BasicComponent(title = "AndroidX DataStore", summary = "Apache-2.0", role = Role.Button,
                onClick = { uri.openUri("https://www.apache.org/licenses/LICENSE-2.0.txt") })
            BasicComponent(title = "kotlinx.coroutines / Okio", summary = "Apache-2.0", role = Role.Button,
                onClick = { uri.openUri("https://www.apache.org/licenses/LICENSE-2.0.txt") })
        } }
        item { SmallTitle("设计参考，未打包为测速核心") }
        item { SettingsCard {
            BasicComponent(title = "clash-speedtest", summary = "faceair · GPL-3.0", role = Role.Button,
                onClick = { uri.openUri("https://github.com/faceair/clash-speedtest/tree/1e1fb9e2bbb29282cce5613a6c6b946b85169fdc") })
            BasicComponent(title = "subs-check-pro", summary = "sinspired · GPL-3.0", role = Role.Button,
                onClick = { uri.openUri("https://github.com/sinspired/subs-check-pro/tree/e53554f9ad2cc9770f483f0c08b7d9ea911c331d") })
        } }
    }
}
