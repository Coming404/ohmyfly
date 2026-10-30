package com.ohmyfly.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HapticSettingsTest {
    private val enabledKey = booleanPreferencesKey("ui.hapticEnabled")
    private val levelKey = stringPreferencesKey("ui.hapticLevel")
    private val schemaKey = intPreferencesKey("schema.version")

    @Test
    fun defaultsAreEnabledAtMediumWithoutWritingMissingKeys() {
        val preferences = mutablePreferencesOf()
        for (settings in listOf(AppSettings(), SettingsCodec.decode(preferences))) {
            assertTrue(settings.hapticEnabled)
            assertEquals(HapticLevel.Medium, settings.hapticLevel)
        }
        assertTrue(preferences.asMap().isEmpty())
        assertEquals(1, SettingsCodec.CURRENT_SCHEMA_VERSION)
    }

    @Test
    fun everyLevelAndEnabledChoiceRoundTripsWithSchemaOne() {
        for (enabled in listOf(false, true)) for (level in HapticLevel.entries) {
            val settings = AppSettings(hapticEnabled = enabled, hapticLevel = level)
            val encoded = SettingsCodec.encode(settings)
            assertEquals(enabled, encoded[enabledKey])
            assertEquals(level.name, encoded[levelKey])
            assertEquals(1, encoded[schemaKey])

            val decoded = SettingsCodec.decode(encoded)
            assertEquals(enabled, decoded.hapticEnabled)
            assertEquals(level, decoded.hapticLevel)
        }
    }

    @Test
    fun missingKeysFallBackIndependentlyWithoutRewritingSchemaOne() {
        val cases = listOf(
            mutablePreferencesOf(schemaKey to 1) to AppSettings(),
            mutablePreferencesOf(schemaKey to 1, enabledKey to false) to AppSettings(hapticEnabled = false),
            mutablePreferencesOf(schemaKey to 1, levelKey to "Heavy") to AppSettings(hapticLevel = HapticLevel.Heavy),
        )
        for ((preferences, expected) in cases) {
            val stored = preferences.asMap().toMap()
            val decoded = SettingsCodec.decode(preferences)
            assertEquals(expected.hapticEnabled, decoded.hapticEnabled)
            assertEquals(expected.hapticLevel, decoded.hapticLevel)
            assertEquals(stored, preferences.asMap())
        }
    }

    @Test
    fun unknownLevelsFallBackWithoutEnablingHapticsOrWritingBack() {
        for (value in listOf("", "Unknown", "2")) {
            val preferences = mutablePreferencesOf(schemaKey to 1, enabledKey to false, levelKey to value)
            val stored = preferences.asMap().toMap()
            val decoded = SettingsCodec.decode(preferences)
            assertFalse(decoded.hapticEnabled)
            assertEquals(HapticLevel.Medium, decoded.hapticLevel)
            assertEquals(stored, preferences.asMap())
        }
    }

    @Test
    fun wrongEnabledTypesFallBackWithoutChangingValidLevelOrStoredValues() {
        val cases = listOf(
            mutablePreferencesOf(stringPreferencesKey("ui.hapticEnabled") to "false"),
            mutablePreferencesOf(intPreferencesKey("ui.hapticEnabled") to 0),
        )
        for (preferences in cases) {
            preferences[levelKey] = HapticLevel.Heavy.name
            val stored = preferences.asMap().toMap()
            val decoded = SettingsCodec.decode(preferences)
            assertTrue(decoded.hapticEnabled)
            assertEquals(HapticLevel.Heavy, decoded.hapticLevel)
            assertEquals(stored, preferences.asMap())
        }
    }

    @Test
    fun wrongLevelTypesFallBackWithoutChangingEnabledChoiceOrStoredValues() {
        val cases = listOf(
            mutablePreferencesOf(intPreferencesKey("ui.hapticLevel") to 2),
            mutablePreferencesOf(booleanPreferencesKey("ui.hapticLevel") to true),
        )
        for (preferences in cases) {
            preferences[enabledKey] = false
            val stored = preferences.asMap().toMap()
            val decoded = SettingsCodec.decode(preferences)
            assertFalse(decoded.hapticEnabled)
            assertEquals(HapticLevel.Medium, decoded.hapticLevel)
            assertEquals(stored, preferences.asMap())
        }
    }
}
