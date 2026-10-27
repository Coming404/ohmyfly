package com.ohmyfly.ui.settings

import androidx.compose.runtime.Composable

// shortcut: relies on platform default toolbar behavior; upgrade only if device verification finds a mismatch.
@Composable
internal actual fun SettingsInputMenu(content: @Composable () -> Unit) = content()
