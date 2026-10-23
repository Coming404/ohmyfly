@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ohmyfly.ui.glass.sensor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.UIKit.UIInterfaceOrientationPortraitUpsideDown
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISceneDidActivateNotification
import platform.UIKit.UISceneWillDeactivateNotification
import platform.darwin.NSObjectProtocol

@Composable
internal actual fun rememberGlassGravity(enabled: Boolean): State<Offset> {
    val zero = remember { mutableStateOf(Offset.Zero) }
    if (!enabled) return zero
    val controller = LocalUIViewController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val gravity = remember(controller, lifecycle) { mutableStateOf(Offset.Zero) }
    DisposableEffect(controller, lifecycle) {
        val center = NSNotificationCenter.defaultCenter
        val observers = mutableListOf<NSObjectProtocol>()
        var unsubscribe: (() -> Unit)? = null
        // Unknown at first attachment is static Zero; later unknown retains the last known mapping.
        var quarterTurns = -1

        fun stopMotion() {
            unsubscribe?.invoke()
            unsubscribe = null
            gravity.value = Offset.Zero
        }
        fun suspendForeground() {
            stopMotion()
            observers.forEach(center::removeObserver)
            observers.clear()
        }
        fun canCollect(): Boolean =
            lifecycle.currentState == Lifecycle.State.RESUMED &&
                UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive &&
                controller.view.window?.windowScene?.activationState ==
                    UISceneActivationStateForegroundActive &&
                !UIAccessibilityIsReduceMotionEnabled()

        fun updateMotion() {
            if (!canCollect()) {
                stopMotion()
            } else if (unsubscribe == null) {
                unsubscribe = GlassMotionSource.subscribe { device ->
                    if (!canCollect()) {
                        stopMotion()
                    } else {
                        quarterTurns = when (controller.view.window?.windowScene?.interfaceOrientation) {
                            UIInterfaceOrientationPortrait -> 0
                            UIInterfaceOrientationLandscapeRight -> 1
                            UIInterfaceOrientationPortraitUpsideDown -> 2
                            UIInterfaceOrientationLandscapeLeft -> 3
                            else -> quarterTurns
                        }
                        gravity.value = screenGlassGravity(device, quarterTurns)
                    }
                }
            }
        }
        fun resumeForeground() {
            if (observers.isEmpty()) {
                observers += center.addObserverForName(
                    UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue,
                ) { suspendForeground() }
                observers += center.addObserverForName(
                    UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue,
                ) { updateMotion() }
                observers += center.addObserverForName(
                    UISceneWillDeactivateNotification, null, NSOperationQueue.mainQueue,
                ) { notification ->
                    if (notification?.`object` == controller.view.window?.windowScene) suspendForeground()
                }
                observers += center.addObserverForName(
                    UISceneDidActivateNotification, null, NSOperationQueue.mainQueue,
                ) { updateMotion() }
                observers += center.addObserverForName(
                    UIAccessibilityReduceMotionStatusDidChangeNotification, null, NSOperationQueue.mainQueue,
                ) { updateMotion() }
            }
            updateMotion()
        }
        // Keep only this lifecycle observer while inactive so ON_RESUME can reinstall native observers.
        val lifecycleObserver = LifecycleEventObserver { _, _ ->
            if (lifecycle.currentState == Lifecycle.State.RESUMED) resumeForeground()
            else suspendForeground()
        }
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
            suspendForeground()
        }
    }
    return gravity
}

/** One app-wide manager; all subscriptions, callbacks and state writes stay on the main queue. */
private object GlassMotionSource {
    private val manager by lazy { CMMotionManager() }
    private val listeners = mutableMapOf<Any, (Offset) -> Unit>()
    private var previous: Offset? = null
    private var running = false
    private var generation = 0

    fun subscribe(listener: (Offset) -> Unit): () -> Unit {
        val token = Any()
        listeners[token] = listener
        previous?.let(listener)
        if (!running) start()
        return {
            listeners.remove(token)
            if (listeners.isEmpty()) stop()
        }
    }

    private fun start() {
        if (!manager.isDeviceMotionAvailable()) return
        previous = null
        running = true
        val session = ++generation
        manager.deviceMotionUpdateInterval = 1.0 / 60.0
        manager.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { motion, error ->
            // Core Motion may already have enqueued an update when the last subscriber stops.
            if (running && session == generation) {
                if (error != null || motion == null) {
                    stop()
                    listeners.values.toList().forEach { it(Offset.Zero) }
                } else {
                    val sample = motion.gravity.useContents { Offset(x.toFloat(), y.toFloat()) }
                    val filtered = smoothGlassGravity(previous, sample)
                    previous = if (sample.x.isFinite() && sample.y.isFinite()) filtered else null
                    listeners.values.toList().forEach { it(filtered) }
                }
            }
        }
    }

    private fun stop() {
        ++generation
        if (running) manager.stopDeviceMotionUpdates()
        running = false
        previous = null
    }
}
