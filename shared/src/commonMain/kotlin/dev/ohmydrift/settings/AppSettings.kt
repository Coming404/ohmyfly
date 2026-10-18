package dev.ohmydrift.settings

import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.MirrorOrder
import dev.ohmydrift.mirror.defaultMirrors
import dev.ohmydrift.ui.ThemeMode

internal data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val reduceTransparency: Boolean = false,
    val speed: SpeedSettings = SpeedSettings(),
    val media: MediaSettings = MediaSettings(),
    val subscription: SubscriptionSettings = SubscriptionSettings(),
    val mirrors: List<MirrorEntry> = defaultMirrors(),
    val selectedMirrorId: String? = null,
    val mirrorOrder: MirrorOrder = MirrorOrder.LatencyAscending,
)
