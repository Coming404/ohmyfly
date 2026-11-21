package com.ohmyfly.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals

class AccentPaletteTest {
    @Test
    fun everyPaletteRoundTripsThroughSettingsCodec() {
        AccentPalette.entries.forEach { palette ->
            val settings = AppSettings(accentPalette = palette)
            assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
        }
    }

    @Test
    fun missingOrUnknownPaletteUsesAutomaticWithoutWritingBack() {
        val key = stringPreferencesKey("ui.accentPalette")
        val missing = mutablePreferencesOf()
        assertEquals(AccentPalette.Automatic, SettingsCodec.decode(missing).accentPalette)
        assertEquals(0, missing.asMap().size)

        val unknown = mutablePreferencesOf(key to "FuturePalette")
        assertEquals(AccentPalette.Automatic, SettingsCodec.decode(unknown).accentPalette)
        assertEquals("FuturePalette", unknown[key])
    }
}
