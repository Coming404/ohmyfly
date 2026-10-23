package com.ohmyfly.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/** Platform accessibility input only; never persisted over the application's own preference. */
@Composable
internal expect fun rememberSystemReduceTransparency(): State<Boolean>
