// 应用设置包含持久化日志策略，恢复全部设置时一并恢复默认策略。
package com.ohmyfly.settings

import com.ohmyfly.logging.LogPolicy

import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.mirror.defaultMirrors
import com.ohmyfly.ui.ThemeMode

internal data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val materialMode: MaterialMode = MaterialMode.LiquidGlass,
    val reduceTransparency: Boolean = false,
    val reduceMotion: Boolean = false,
    val hapticEnabled: Boolean = true,
    val hapticLevel: HapticLevel = HapticLevel.Medium,
    val mirrorEnabled: Boolean = true,
    val speed: SpeedSettings = SpeedSettings(),
    val media: MediaSettings = MediaSettings(),
    val logging: LogPolicy = LogPolicy(),
    val subscription: SubscriptionSettings = SubscriptionSettings(),
    val mirrors: List<MirrorEntry> = defaultMirrors(),
    val selectedMirrorId: String? = null,
)
