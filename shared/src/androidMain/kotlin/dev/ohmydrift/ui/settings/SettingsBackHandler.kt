package dev.ohmydrift.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal actual fun SettingsBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled, onBack)
}
