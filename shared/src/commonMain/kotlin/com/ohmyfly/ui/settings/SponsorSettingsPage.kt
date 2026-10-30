package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

/** Placeholder until sponsor channels and the sponsor list have real data. */
@Composable
internal fun SponsorSettingsPage(padding: PaddingValues) {
    SettingsList(padding) {
        item { SettingsPageIntro(SettingsPage.Sponsor.title) }
    }
}
