package com.ohmyfly.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import com.ohmyfly.settings.SettingsSession

internal val LocalSettingsPage = staticCompositionLocalOf { SettingsPage.Home }

@Composable
internal fun SettingsPageScope(page: SettingsPage, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSettingsPage provides page, content = content)
}

@Composable
internal fun SettingsEditHost(session: SettingsSession, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val edits = remember(session, scope) { SettingsEdits(session, scope) }
    val confirmed = session.state.confirmedSettings
    LaunchedEffect(confirmed) { if (confirmed != null) edits.sync(confirmed) }
    CompositionLocalProvider(LocalSettingsEdits provides edits, content = content)
}
