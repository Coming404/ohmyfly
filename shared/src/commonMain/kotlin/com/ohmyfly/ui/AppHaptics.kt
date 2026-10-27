package com.ohmyfly.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ohmyfly.settings.HapticLevel

internal class AppHapticFeedback(
    private val enabled: () -> Boolean,
    private val level: () -> HapticLevel,
    private val perform: (HapticLevel) -> Unit,
) {
    fun click() = preview(level())

    fun preview(level: HapticLevel) {
        if (enabled()) perform(level)
    }
}

internal val LocalAppHaptics = staticCompositionLocalOf {
    AppHapticFeedback(enabled = { false }, level = { HapticLevel.Medium }, perform = {})
}

@Composable
internal expect fun rememberPlatformHaptics(): (HapticLevel) -> Unit

@Composable
internal fun AppHaptics(enabled: Boolean, level: HapticLevel, content: @Composable () -> Unit) {
    val currentEnabled = rememberUpdatedState(enabled)
    val currentLevel = rememberUpdatedState(level)
    val perform = rememberPlatformHaptics()
    val feedback = remember(perform) {
        AppHapticFeedback({ currentEnabled.value }, { currentLevel.value }, perform)
    }
    val system = LocalHapticFeedback.current
    val componentFeedback = remember(feedback, system) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (!currentEnabled.value) return
                // Miuix menus own their click feedback; don't wrap their selection callbacks again.
                when (hapticFeedbackType) {
                    HapticFeedbackType.ContextClick, HapticFeedbackType.Confirm,
                    HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff -> feedback.click()
                    else -> system.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalAppHaptics provides feedback,
        LocalHapticFeedback provides componentFeedback,
        content = content,
    )
}

// Wrap actual activation callbacks, not press/release events: cancellation and disabled items stay silent.
@Composable
internal fun hapticClick(onClick: () -> Unit): () -> Unit {
    val feedback = LocalAppHaptics.current
    return {
        onClick()
        feedback.click()
    }
}

@Composable
internal fun <T> hapticChange(onChange: (T) -> Unit): (T) -> Unit {
    val feedback = LocalAppHaptics.current
    return { value ->
        onChange(value)
        feedback.click()
    }
}
