package com.ohmyfly.ui

internal enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色模式"),
    Dark("深色模式");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }
}
