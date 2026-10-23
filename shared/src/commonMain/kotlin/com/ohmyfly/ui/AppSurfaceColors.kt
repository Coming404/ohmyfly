package com.ohmyfly.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import top.yukonga.miuix.kmp.theme.Colors

internal data class AppSurfaceColors(
    val page: Color,
    val card: Color,
    val onCard: Color,
    val cardOutline: Color,
)

internal val LocalAppSurfaceColors = staticCompositionLocalOf<AppSurfaceColors> {
    error("App surfaces require AppTheme")
}

internal fun interpolateAppSurfaceColors(
    start: AppSurfaceColors,
    end: AppSurfaceColors,
    fraction: Float,
): AppSurfaceColors {
    val progress = fraction.coerceIn(0f, 1f)
    return AppSurfaceColors(
        page = lerp(start.page, end.page, progress),
        card = lerp(start.card, end.card, progress),
        onCard = lerp(start.onCard, end.onCard, progress),
        cardOutline = lerp(start.cardOutline, end.cardOutline, progress),
    )
}

@Composable
internal fun rememberAnimatedAppSurfaceColors(target: AppSurfaceColors, motionAllowed: Boolean): AppSurfaceColors {
    var current by remember { mutableStateOf(target) }
    LaunchedEffect(target, motionAllowed) {
        val start = current
        if (!motionAllowed || start == target) {
            current = target
        } else {
            animate(0f, 1f, animationSpec = tween(AppTransitionDurationMillis)) { progress, _ ->
                current = interpolateAppSurfaceColors(start, target, progress)
            }
            current = target
        }
    }
    return if (motionAllowed) current else target
}

internal fun appSurfaceColors(colors: Colors, dynamic: Boolean, dark: Boolean): AppSurfaceColors {
    val lightDynamic = dynamic && !dark
    return AppSurfaceColors(
        page = when {
            dark -> colors.surface
            dynamic -> colors.surfaceContainerHigh
            else -> Color(0xFFF0F1F3)
        },
        card = if (lightDynamic) colors.surface else colors.surfaceContainer,
        onCard = if (lightDynamic) colors.onSurface else colors.onSurfaceContainer,
        cardOutline = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f),
    )
}
