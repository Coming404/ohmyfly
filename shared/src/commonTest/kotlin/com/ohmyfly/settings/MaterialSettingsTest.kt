package com.ohmyfly.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ohmyfly.ui.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MaterialSettingsTest {
    private val materialKey = stringPreferencesKey("ui.materialMode")

    @Test
    fun defaultsSelectLiquidGlass() {
        assertEquals(MaterialMode.LiquidGlass, AppSettings().materialMode)
        val decoded = SettingsCodec.decode(mutablePreferencesOf().toPreferences())
        assertEquals(MaterialMode.LiquidGlass, decoded.materialMode)
        assertFalse(decoded.reduceTransparency)
    }

    @Test
    fun everyMaterialRoundTripsWithStableNames() {
        val names = listOf("GaussianBlur", "HighlightGlass", "SoftGlass", "LiquidGlass")
        assertEquals(names, MaterialMode.entries.map { it.name })
        for (mode in MaterialMode.entries) {
            val preferences = SettingsCodec.encode(AppSettings(materialMode = mode))
            assertEquals(mode.name, preferences[materialKey])
            assertEquals(mode, SettingsCodec.decode(preferences.toPreferences()).materialMode)
        }
    }

    @Test
    fun legacyPreferencesKeepExistingChoicesAndUnknownKeys() {
        val preferences = SettingsCodec.encode(AppSettings(
            themeMode = ThemeMode.Dark,
            reduceTransparency = true,
            speed = SpeedSettings(downloadMiB = 32),
            subscription = SubscriptionSettings(retries = 1),
        ))
        preferences.remove(materialKey)
        val foreignKey = stringPreferencesKey("future.option")
        preferences[foreignKey] = "keep"
        val before = preferences.toPreferences()
        val decoded = SettingsCodec.decode(before)

        assertEquals(MaterialMode.LiquidGlass, decoded.materialMode)
        assertEquals(ThemeMode.Dark, decoded.themeMode)
        assertEquals(true, decoded.reduceTransparency)
        assertEquals(32, decoded.speed.downloadMiB)
        assertEquals(1, decoded.subscription.retries)
        assertNull(preferences[materialKey])
        assertEquals(before, preferences.toPreferences())

        SettingsCodec.write(preferences, decoded.copy(materialMode = MaterialMode.HighlightGlass))
        assertEquals("keep", preferences[foreignKey])
        assertEquals("HighlightGlass", preferences[materialKey])
        assertEquals(1, preferences[intPreferencesKey("schema.version")])
        assertEquals(decoded.speed, SettingsCodec.decode(preferences.toPreferences()).speed)
    }

    @Test
    fun unknownMaterialFallsBackWithoutRewritingStoredValue() {
        for (raw in listOf("", "unknown", "液态玻璃", "Off", "SoftBlur", "ProgressiveBlur")) {
            val preferences = mutablePreferencesOf(materialKey to raw).toPreferences()
            assertEquals(MaterialMode.LiquidGlass, SettingsCodec.decode(preferences).materialMode)
            assertEquals(raw, preferences[materialKey])
        }
        val known = mutablePreferencesOf(materialKey to "gaussianblur").toPreferences()
        assertEquals(MaterialMode.GaussianBlur, SettingsCodec.decode(known).materialMode)
    }

    @Test
    fun wrongMaterialTypeFallsBackWithoutRewritingStoredValue() {
        val wrongKey = booleanPreferencesKey("ui.materialMode")
        val preferences = mutablePreferencesOf(
            wrongKey to true,
            booleanPreferencesKey("ui.themeMode") to true,
            stringPreferencesKey("speed.downloadMiB") to "wrong-type",
        ).toPreferences()
        val decoded = SettingsCodec.decode(preferences)
        assertEquals(MaterialMode.LiquidGlass, decoded.materialMode)
        assertEquals(ThemeMode.System, decoded.themeMode)
        assertEquals(SpeedSettings().downloadMiB, decoded.speed.downloadMiB)
        assertEquals(true, preferences[wrongKey])
        assertEquals("wrong-type", preferences[stringPreferencesKey("speed.downloadMiB")])
    }

    @Test
    fun reducingTransparencyDoesNotReplaceMaterialSelection() {
        for (mode in MaterialMode.entries) {
            val preferences = SettingsCodec.encode(AppSettings(materialMode = mode, reduceTransparency = true))
            val reduced = SettingsCodec.decode(preferences.toPreferences())
            assertEquals(mode, reduced.materialMode)
            assertEquals(true, reduced.reduceTransparency)
            SettingsCodec.write(preferences, reduced.copy(reduceTransparency = false))
            val restored = SettingsCodec.decode(preferences.toPreferences())
            assertEquals(mode, restored.materialMode)
            assertFalse(restored.reduceTransparency)
        }
    }
}
