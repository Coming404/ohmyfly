package dev.ohmydrift.ui.settings

import androidx.compose.runtime.Composable

// iOS uses the explicit header button; no native swipe-back is claimed.
@Composable
internal actual fun SettingsBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
