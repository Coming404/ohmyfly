package dev.ohmydrift.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale

internal const val AppTransitionDurationMillis = 180
internal val LocalAppMotionAllowed = staticCompositionLocalOf { true }

internal fun appMotionAllowed(reduceMotion: Boolean, systemScale: Float): Boolean =
    !reduceMotion && systemScale > 0f

/** App-owned animations only; Miuix internals still use the platform's coroutine scale. */
@Composable
internal fun AppMotion(reduceMotion: Boolean, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val systemScale = scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    CompositionLocalProvider(
        LocalAppMotionAllowed provides appMotionAllowed(reduceMotion, systemScale),
        content = content,
    )
}
