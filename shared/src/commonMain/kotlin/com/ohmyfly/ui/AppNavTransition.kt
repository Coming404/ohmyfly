package com.ohmyfly.ui

import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

private const val APP_NAV_BASE_STIFFNESS = 400f

// 与主导航相同，时长约增加 35%；保留原有阻尼、过冲和速度响应。
private val appNavSettleSpec = NavSettleSpec.Spring(
    dampingRatio = 0.72f,
    stiffness = APP_NAV_BASE_STIFFNESS / (AppNavigationDurationMultiplier * AppNavigationDurationMultiplier),
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
