package com.ohmyfly.settings

import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.mirror.defaultMirrors
import com.ohmyfly.ui.ThemeMode

internal data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val materialMode: MaterialMode = MaterialMode.LiquidGlass,
    val reduceTransparency: Boolean = false,
    val reduceMotion: Boolean = false,
    val mirrorEnabled: Boolean = true,
    val speed: SpeedSettings = SpeedSettings(),
    val media: MediaSettings = MediaSettings(),
    val subscription: SubscriptionSettings = SubscriptionSettings(),
    val mirrors: List<MirrorEntry> = defaultMirrors(),
    val selectedMirrorId: String? = null,
)
