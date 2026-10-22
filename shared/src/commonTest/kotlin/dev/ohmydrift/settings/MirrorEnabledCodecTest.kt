package dev.ohmydrift.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.ohmydrift.mirror.defaultMirrors
import dev.ohmydrift.ui.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 镜像启用开关是可选 key：旧文件不受影响，停用只改一个字段。 */
class MirrorEnabledCodecTest {
    @Test
    fun missingEnabledKeyDefaultsToTrueWithoutRewritingOtherFields() {
        val stored = mutablePreferencesOf(
            intPreferencesKey("schema.version") to 1,
            stringPreferencesKey("ui.themeMode") to "Dark",
            booleanPreferencesKey("ui.reduceMotion") to true,
            stringSetPreferencesKey("media.platforms") to setOf("netflix"),
            intPreferencesKey("mirror.count") to 1,
            stringPreferencesKey("mirror.0.id") to "a",
            stringPreferencesKey("mirror.0.address") to "https://a.example",
        ).toPreferences()

        val decoded = SettingsCodec.decode(stored)

        assertTrue(decoded.mirrorEnabled)
        assertEquals(ThemeMode.Dark, decoded.themeMode)
        assertTrue(decoded.reduceMotion)
        assertEquals(setOf("netflix"), decoded.media.platforms)
        assertEquals(listOf("a"), decoded.mirrors.map { it.id })
        // 解码只回落内存值，原文件不因缺失开关被重写。
        assertNull(stored[booleanPreferencesKey("mirror.enabled")])
    }

    @Test
    fun enabledRoundTripsAndSchemaStaysOne() {
        val disabled = AppSettings(mirrorEnabled = false, selectedMirrorId = defaultMirrors().first().id)
        val encoded = SettingsCodec.encode(disabled)

        assertEquals(1, encoded[intPreferencesKey("schema.version")])
        assertEquals(false, encoded[booleanPreferencesKey("mirror.enabled")])

        val decoded = SettingsCodec.decode(encoded)
        assertFalse(decoded.mirrorEnabled)
        // 停用不清空配置，也不改变已保留的选择。
        assertEquals(defaultMirrors().map { it.id }, decoded.mirrors.map { it.id })
        assertEquals(disabled.selectedMirrorId, decoded.selectedMirrorId)
        assertNull(validateSettings(decoded))
    }

    @Test
    fun disablingMirrorsLeavesEveryOtherStoredFieldIntact() {
        val base = AppSettings(themeMode = ThemeMode.Dark)
        val preferences = SettingsCodec.encode(base)
        SettingsCodec.write(preferences, base.copy(mirrorEnabled = false))

        val decoded = SettingsCodec.decode(preferences)
        assertFalse(decoded.mirrorEnabled)
        assertEquals(base.themeMode, decoded.themeMode)
        assertEquals(base.speed, decoded.speed)
        assertEquals(base.media, decoded.media)
        assertEquals(base.subscription, decoded.subscription)
    }

    @Test
    fun emptyMirrorListCanRemainEnabled() {
        val preferences = mutablePreferencesOf(
            intPreferencesKey("mirror.count") to 0,
            booleanPreferencesKey("mirror.enabled") to true,
        ).toPreferences()

        val decoded = SettingsCodec.decode(preferences)

        assertTrue(decoded.mirrors.isEmpty())
        assertTrue(decoded.mirrorEnabled)
        assertNull(validateSettings(decoded))
    }

}
