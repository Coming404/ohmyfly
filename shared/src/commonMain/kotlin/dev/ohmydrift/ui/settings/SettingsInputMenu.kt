package dev.ohmydrift.ui.settings

import androidx.compose.runtime.Composable

/** Applied only around settings inputs, not global text selection or the URL preview. */
@Composable
internal expect fun SettingsInputMenu(content: @Composable () -> Unit)
