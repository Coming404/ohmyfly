package com.ohmyfly.ui.settings

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
internal enum class SettingsPage(val title: String) : NavKey {
    Home("设置"),
    Speed("起飞与延迟"),
    Media("流媒体与 AI"),
    Subscription("塔台与过滤"),
    Mirrors("GitHub 镜像"),
    Privacy("数据与隐私"),
    About("关于"),
}

/** Settings currently has one child level; ignore stale/double taps from the home entry. */
internal fun NavBackStack.openSettingsPage(page: SettingsPage) {
    if (lastOrNull() == SettingsPage.Home && page != SettingsPage.Home) add(page)
}

internal fun NavBackStack.popSettingsPage() {
    if (size > 1) removeAt(lastIndex)
}
