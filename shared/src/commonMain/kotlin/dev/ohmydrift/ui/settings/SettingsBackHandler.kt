package dev.ohmydrift.ui.settings

import androidx.compose.runtime.Composable

@Composable
internal expect fun SettingsBackHandler(enabled: Boolean, onBack: () -> Unit)
