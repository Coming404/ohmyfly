package com.ohmyfly

import androidx.compose.ui.window.ComposeUIViewController
import com.ohmyfly.settings.iosSettingsPath
import platform.Foundation.NSBundle

fun MainViewController(): platform.UIKit.UIViewController {
    val path = iosSettingsPath()
    val bundle = NSBundle.mainBundle
    val name = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "未知版本"
    val build = bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "?"
    return ComposeUIViewController {
        App(dynamicColorSeed = null, settingsPath = path, version = "$name ($build) · iOS")
    }
}
