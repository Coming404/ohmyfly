package com.ohmyfly.ui

import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec

// Keep all NavDisplay settle phases aligned with the root pager's slightly underdamped spring.
private val appNavSettleSpec = NavSettleSpec.Spring(
    dampingRatio = PagerNavigationSpringSpec.dampingRatio,
    stiffness = PagerNavigationSpringSpec.stiffness,
    clampOvershoot = false,
)

private val appNavMotion = NavMotion(
    commit = appNavSettleSpec,
    cancel = appNavSettleSpec,
    programmatic = appNavSettleSpec,
)

/** Miuix-default slide/parallax geometry, driven by the app's shared spring settle motion. */
internal val AppNavTransition: NavTransition = navGraphicsTransition(
    motion = appNavMotion,
) { scope ->
    val depth = scope.relativeDepth
    val width = scope.layoutSize.width.toFloat()
    val rtl = scope.layoutDirection == LayoutDirection.Rtl
    if (depth <= 0f) {
        val sign = if (rtl) -1f else 1f
        translationX = (sign * (-depth).coerceIn(0f, 1f) * width).roundToInt().toFloat()
    } else {
        val covered = depth.coerceIn(0f, 1f)
        translationX = (if (rtl) 1f else -1f) * covered * width * 0.25f
        alpha = 1f - 0.1f * covered
    }
}
