@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.ohmydrift.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled
import platform.UIKit.UIAccessibilityReduceTransparencyStatusDidChangeNotification
import platform.UIKit.UIApplicationDidBecomeActiveNotification

@Composable
internal actual fun rememberSystemReduceTransparency(): State<Boolean> {
    val reduced = remember { mutableStateOf(UIAccessibilityIsReduceTransparencyEnabled()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        fun refresh() { reduced.value = UIAccessibilityIsReduceTransparencyEnabled() }
        val observers = listOf(
            UIAccessibilityReduceTransparencyStatusDidChangeNotification,
            UIApplicationDidBecomeActiveNotification,
        ).map { name ->
            center.addObserverForName(name, null, NSOperationQueue.mainQueue) { refresh() }
        }
        // Refresh after registering too, so a change between composition and this effect is not lost.
        refresh()
        onDispose { observers.forEach(center::removeObserver) }
    }
    return reduced
}
