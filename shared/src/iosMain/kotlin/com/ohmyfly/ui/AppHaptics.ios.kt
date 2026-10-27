@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ohmyfly.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import com.ohmyfly.settings.HapticLevel
import kotlinx.cinterop.cValue
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSThread
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

@Suppress("DEPRECATION") // The legacy initializer is required on iOS 16–17.4.
@Composable
internal actual fun rememberPlatformHaptics(): (HapticLevel) -> Unit {
    val view = LocalUIViewController.current.view
    return remember(view) {
        val supportsView = NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
            cValue<NSOperatingSystemVersion> {
                majorVersion = 17
                minorVersion = 5
                patchVersion = 0
            },
        )
        val generators = mutableMapOf<HapticLevel, UIImpactFeedbackGenerator>()
        val perform: (HapticLevel) -> Unit = { level ->
            val feedback: () -> Unit = {
                if (view.window != null) {
                    val generator = generators.getOrPut(level) {
                        val style = when (level) {
                            HapticLevel.Light -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
                            HapticLevel.Medium -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
                            HapticLevel.Heavy -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
                        }
                        if (supportsView) UIImpactFeedbackGenerator(style = style, view = view)
                        else UIImpactFeedbackGenerator(style = style)
                    }
                    // UIKit ignores feedback on devices without haptic hardware.
                    generator.prepare()
                    generator.impactOccurred()
                }
            }
            if (NSThread.isMainThread) feedback()
            else NSOperationQueue.mainQueue.addOperationWithBlock(feedback)
        }
        perform
    }
}
