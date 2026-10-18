package dev.ohmydrift

import androidx.compose.ui.window.ComposeUIViewController
import dev.ohmydrift.settings.iosSettingsPath
import platform.Foundation.NSBundle

fun MainViewController(): platform.UIKit.UIViewController {
    val path = iosSettingsPath()
    val bundle = NSBundle.mainBundle
    val name = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "未知版本"
    val build = bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "?"
    return ComposeUIViewController {
        App(useSystemDynamicColors = false, settingsPath = path, version = "$name ($build) · iOS")
    }
}
