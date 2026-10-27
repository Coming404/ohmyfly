package com.ohmyfly.ui

import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.ohmyfly.settings.HapticLevel

@Composable
internal actual fun rememberPlatformHaptics(): (HapticLevel) -> Unit {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) {
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        val handler = Handler(Looper.getMainLooper())
        val attributes = VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_TOUCH)
            .build()
        val effects = HapticLevel.entries.associateWith { level ->
            VibrationEffect.createPredefined(
                when (level) {
                    HapticLevel.Light -> VibrationEffect.EFFECT_TICK
                    HapticLevel.Medium -> VibrationEffect.EFFECT_CLICK
                    HapticLevel.Heavy -> VibrationEffect.EFFECT_HEAVY_CLICK
                },
            )
        }
        val perform: (HapticLevel) -> Unit = { level ->
            val feedback = Runnable {
                if (vibrator?.hasVibrator() == true &&
                    view.isAttachedToWindow && view.isHapticFeedbackEnabled &&
                    Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.HAPTIC_FEEDBACK_ENABLED,
                        1,
                    ) != 0
                ) {
                    vibrator.vibrate(effects.getValue(level), attributes)
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) feedback.run()
            else handler.post(feedback)
        }
        perform
    }
}
