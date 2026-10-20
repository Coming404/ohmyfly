package dev.ohmydrift.ui.glass.sensor

import android.hardware.display.DisplayManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt

@Composable
internal actual fun rememberGlassGravity(enabled: Boolean): State<Offset> {
    val zero = remember { mutableStateOf(Offset.Zero) }
    if (!enabled) return zero
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    // Conditional composition disposes Miuix's listener/observer, including on ON_PAUSE.
    if (lifecycleState != Lifecycle.State.RESUMED) return zero

    val view = LocalView.current
    val context = LocalContext.current
    val rotation = remember(view) { mutableStateOf(view.display?.rotation ?: -1) }
    DisposableEffect(context, view) {
        val displays = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) {
                rotation.value = view.display?.rotation ?: -1
            }
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == view.display?.displayId) {
                    rotation.value = view.display?.rotation ?: -1
                }
            }
        }
        displays?.registerDisplayListener(listener, null)
        onDispose { displays?.unregisterDisplayListener(listener) }
    }
    // 0.9.4 already low-passes in device coordinates; do not filter again here.
    val tilt = rememberDeviceTilt(smoothing = GlassGravitySmoothing)
    return remember(tilt, rotation) {
        derivedStateOf {
            val sample = tilt.value
            screenGlassGravity(Offset(sample.gravityX, sample.gravityY), rotation.value)
        }
    }
}
