package com.ohmyfly.ui

import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

// About 4% full-step overshoot; subpage motion is independent of the root pager.
private val appNavSettleSpec = NavSettleSpec.Spring(
    dampingRatio = 0.72f,
    stiffness = 400f,
    clampOvershoot = false,
)

private val appNavMotion = NavMotion(
    commit = appNavSettleSpec,
    cancel = appNavSettleSpec,
    programmatic = appNavSettleSpec,
)

// A role derived from depth changes at zero; keep one continuous, opaque slide across it.
internal fun appNavTranslationX(relativeDepth: Float, width: Int, rtl: Boolean): Float =
    (if (rtl) 1f else -1f) * relativeDepth * width

/** Horizontal hierarchy push/pop with the same geometry for finger tracking and spring settle. */
internal val AppNavTransition: NavTransition = navGraphicsTransition(
    motion = appNavMotion,
) { scope ->
    alpha = 1f
    translationX = appNavTranslationX(
        relativeDepth = scope.relativeDepth,
        width = scope.layoutSize.width,
        rtl = scope.layoutDirection == LayoutDirection.Rtl,
    )
}
