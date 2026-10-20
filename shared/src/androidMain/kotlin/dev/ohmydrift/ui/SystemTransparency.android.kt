package dev.ohmydrift.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberSystemReduceTransparency(): State<Boolean> =
    // No equivalent system transparency preference is exposed here; keep the app toggle authoritative.
    remember { mutableStateOf(false) }
