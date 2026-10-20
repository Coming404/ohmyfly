package dev.ohmydrift.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionSettingsTest {
    @Test
    fun oldConfigurationKeepsMotionEnabledAndDoesNotWriteBack() {
        val preferences = mutablePreferencesOf(stringPreferencesKey("ui.themeMode") to "Dark")
        assertFalse(SettingsCodec.decode(preferences).reduceMotion)
        assertEquals(1, preferences.asMap().size)
        assertFalse(preferences.contains(booleanPreferencesKey("ui.reduceMotion")))
    }

    @Test
    fun motionChoiceRoundTripsIndependentlyOfMaterialAndTransparency() {
        for (material in MaterialMode.entries) for (transparent in listOf(false, true)) {
            for (reduced in listOf(false, true)) {
                val settings = AppSettings(materialMode = material, reduceTransparency = transparent, reduceMotion = reduced)
                val decoded = SettingsCodec.decode(SettingsCodec.encode(settings))
                assertEquals(reduced, decoded.reduceMotion)
                assertEquals(material, decoded.materialMode)
                assertEquals(transparent, decoded.reduceTransparency)
            }
        }
    }

    @Test
    fun wrongTypeFallsBackWithoutChangingStoredValue() {
        val key = stringPreferencesKey("ui.reduceMotion")
        val preferences = mutablePreferencesOf(key to "yes")
        assertFalse(SettingsCodec.decode(preferences).reduceMotion)
        assertEquals("yes", preferences[key])
    }

    @Test
    fun writeKeepsUnknownKeysAndSchemaOne() {
        val key = stringPreferencesKey("future.preference")
        val preferences = mutablePreferencesOf(key to "keep")
        SettingsCodec.write(preferences, AppSettings(reduceMotion = true))
        assertTrue(SettingsCodec.decode(preferences).reduceMotion)
        assertEquals("keep", preferences[key])
        assertEquals(1, SettingsCodec.CURRENT_SCHEMA_VERSION)
    }
}
