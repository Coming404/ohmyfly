package dev.ohmydrift.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.MirrorMode
import dev.ohmydrift.mirror.MirrorOrder
import dev.ohmydrift.mirror.defaultMirrors
import dev.ohmydrift.ui.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsCodecTest {
    private val fractionalSpeed = 0.125

    @Test
    fun roundTripKeepsEveryField() {
        val settings = AppSettings(
            themeMode = ThemeMode.Dark,
            materialMode = MaterialMode.SoftGlass,
            reduceTransparency = true,
            reduceMotion = true,
            speed = SpeedSettings(
                mode = SpeedMode.Full,
                latencyUrl = "https://example.com/204",
                downloadUrl = "https://example.com/file.bin",
                downloadMiB = 32,
                uploadMiB = 7,
                transferTimeoutSeconds = 25,
                latencyConcurrency = 3,
                latencyTimeoutSeconds = 9,
                latencySamples = 2,
                connectionsPerNode = 6,
                cellularConfirm = false,
                maxLatencyMs = 800,
                maxFailurePercent = 40,
                minDownloadMiBps = fractionalSpeed,
                minUploadMiBps = 1.5,
                stopAfter = 12,
                renameExport = true,
            ),
            media = MediaSettings(
                enabled = true,
                platforms = setOf("netflix", "youtube"),
                concurrency = 4,
                timeoutSeconds = 20,
                retries = 0,
            ),
            subscription = SubscriptionSettings(
                timeoutSeconds = 30,
                retries = 1,
                maxMiB = 8,
                userAgent = "Drift/1.0",
                includePattern = "^HK",
                excludePattern = "过期",
                protocols = setOf("ss", "trojan"),
            ),
            mirrors = listOf(
                MirrorEntry(id = "a", name = "甲", address = "https://a.example", mode = MirrorMode.Prefix),
                MirrorEntry(id = "b", name = "乙", address = "https://b.example/{url}", mode = MirrorMode.Template),
            ),
            selectedMirrorId = "b",
            mirrorOrder = MirrorOrder.Manual,
        )

        val decoded = SettingsCodec.decode(SettingsCodec.encode(settings))

        assertEquals(settings.themeMode, decoded.themeMode)
        assertEquals(settings.materialMode, decoded.materialMode)
        assertEquals(settings.reduceTransparency, decoded.reduceTransparency)
        assertEquals(settings.reduceMotion, decoded.reduceMotion)
        assertEquals(settings.speed, decoded.speed)
        assertEquals(fractionalSpeed, decoded.speed.minDownloadMiBps, 0.0)
        assertEquals(settings.subscription, decoded.subscription)
        assertEquals(settings.selectedMirrorId, decoded.selectedMirrorId)
        assertEquals(settings.mirrorOrder, decoded.mirrorOrder)
        assertEquals(listOf("a", "b"), decoded.mirrors.map { it.id })
        assertEquals(listOf(MirrorMode.Prefix, MirrorMode.Template), decoded.mirrors.map { it.mode })
        assertNull(validateSettings(decoded))
    }

    @Test
    fun emptyPreferencesFallBackToDefaults() {
        val decoded = SettingsCodec.decode(mutablePreferencesOf().toPreferences())

        assertEquals(AppSettings().speed, decoded.speed)
        assertEquals(AppSettings().media, decoded.media)
        assertEquals(AppSettings().subscription, decoded.subscription)
        assertEquals(ThemeMode.System, decoded.themeMode)
        assertEquals(0.0, decoded.speed.minDownloadMiBps, 0.0)
        assertEquals(defaultMirrors().map { it.id }, decoded.mirrors.map { it.id })
        assertNull(decoded.selectedMirrorId)
    }

    @Test
    fun damagedNumbersAndEnumsFallBackWithoutTouchingStoredValues() {
        val doubleKey = doublePreferencesKey("speed.minDownloadMiBps")
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("ui.themeMode") to "neon",
            stringPreferencesKey("speed.mode") to "warp",
            intPreferencesKey("speed.downloadMiB") to -4,
            intPreferencesKey("speed.latencySamples") to 0,
            intPreferencesKey("speed.connectionsPerNode") to 99,
            doubleKey to Double.NaN,
            stringPreferencesKey("speed.latencyUrl") to "https://user:pw@example.com/204",
            stringSetPreferencesKey("media.platforms") to setOf(" netflix ", ""),
            intPreferencesKey("mirror.count") to 1,
            stringPreferencesKey("mirror.0.id") to "m1",
            stringPreferencesKey("mirror.0.address") to "https://mirror.example",
        ).toPreferences()

        val decoded = SettingsCodec.decode(preferences)
        val defaults = SpeedSettings()

        assertEquals(ThemeMode.System, decoded.themeMode)
        assertEquals(SpeedMode.Download, decoded.speed.mode)
        assertEquals(defaults.downloadMiB, decoded.speed.downloadMiB)
        assertEquals(defaults.latencySamples, decoded.speed.latencySamples)
        assertEquals(defaults.connectionsPerNode, decoded.speed.connectionsPerNode)
        assertEquals(0.0, decoded.speed.minDownloadMiBps, 0.0)
        assertEquals(defaults.latencyUrl, decoded.speed.latencyUrl)
        assertEquals(setOf("netflix"), decoded.media.platforms)
        // 回落只发生在内存，原文件仍是被拒绝的数值，避免每次编辑都写坏原件。
        assertEquals(-4, preferences[intPreferencesKey("speed.downloadMiB")])
        assertTrue(preferences[doubleKey]!!.isNaN())
        assertNull(validateSettings(decoded))
    }

    @Test
    fun infiniteMinSpeedFallsBack() {
        val preferences = mutablePreferencesOf(
            doublePreferencesKey("speed.minUploadMiBps") to Double.POSITIVE_INFINITY,
        ).toPreferences()

        assertEquals(0.0, SettingsCodec.decode(preferences).speed.minUploadMiBps, 0.0)
    }

    @Test
    fun emptyStoredPlatformSetStaysEmptyButAllInvalidFallsBack() {
        val cleared = mutablePreferencesOf(stringSetPreferencesKey("media.platforms") to emptySet()).toPreferences()
        assertTrue(SettingsCodec.decode(cleared).media.platforms.isEmpty())

        val invalid = mutablePreferencesOf(stringSetPreferencesKey("media.platforms") to setOf(" ")).toPreferences()
        assertEquals(MediaSettings().platforms, SettingsCodec.decode(invalid).media.platforms)
    }

    @Test
    fun futureSchemaVersionIsReported() {
        val preferences = mutablePreferencesOf(intPreferencesKey("schema.version") to 99).toPreferences()

        val error = assertFailsWith<SettingsSchemaException> { SettingsCodec.decode(preferences) }
        assertEquals(99, error.foundVersion)
        assertEquals(1, error.supportedVersion)
        assertTrue(error.message!!.contains("99"))
    }

    @Test
    fun damagedMirrorSlotsAreSkippedAndMissingCountUsesDefaults() {
        val damaged = mutablePreferencesOf(
            intPreferencesKey("mirror.count") to 4,
            stringPreferencesKey("mirror.0.id") to "only-id",
            stringPreferencesKey("mirror.1.id") to "ok",
            stringPreferencesKey("mirror.1.address") to "https://ok.example/{url}",
            stringPreferencesKey("mirror.1.mode") to "template",
            stringPreferencesKey("mirror.2.id") to "direct",
            stringPreferencesKey("mirror.2.address") to "https://direct.example",
            stringPreferencesKey("mirror.3.id") to "OK",
            stringPreferencesKey("mirror.3.address") to "https://ok-2.example",
        ).toPreferences()

        val decoded = SettingsCodec.decode(damaged)

        assertEquals(listOf("ok"), decoded.mirrors.map { it.id })
        assertEquals(MirrorMode.Template, decoded.mirrors.single().mode)
        assertEquals("ok", decoded.mirrors.single().name)
        assertEquals(defaultMirrors().map { it.id }, SettingsCodec.decode(mutablePreferencesOf().toPreferences()).mirrors.map { it.id })
    }

    @Test
    fun selectedMirrorMustExist() {
        val preferences = mutablePreferencesOf(
            intPreferencesKey("mirror.count") to 1,
            stringPreferencesKey("mirror.0.id") to "a",
            stringPreferencesKey("mirror.0.address") to "https://a.example",
            stringPreferencesKey("mirror.selectedId") to "gone",
        ).toPreferences()

        assertNull(SettingsCodec.decode(preferences).selectedMirrorId)
    }

    @Test
    fun decodingPreservesPathCaseAndCanonicalSelection() {
        val preferences = SettingsCodec.encode(AppSettings(mirrors = listOf(
            MirrorEntry("one", "One", "https://mirror.example/Path/"),
            MirrorEntry("two", "Two", "https://mirror.example/path/"),
        )))
        preferences[stringPreferencesKey("mirror.selectedId")] = "ONE"
        val decoded = SettingsCodec.decode(preferences)
        assertEquals(2, decoded.mirrors.size)
        assertEquals("one", decoded.selectedMirrorId)
        assertNull(validateSettings(decoded))
    }

    @Test
    fun writeKeepsForeignKeysAndClearsRemovedMirrorSlots() {
        val target = mutablePreferencesOf(
            booleanPreferencesKey("future.flag") to true,
            stringPreferencesKey("mirror.3.id") to "stale",
        )
        val settings = AppSettings(mirrors = listOf(MirrorEntry(id = "a", name = "甲", address = "https://a.example", mode = MirrorMode.Prefix)))

        SettingsCodec.write(target, settings)

        assertEquals(true, target[booleanPreferencesKey("future.flag")])
        assertNull(target[stringPreferencesKey("mirror.3.id")])
        val decoded = SettingsCodec.decode(target.toPreferences())
        assertEquals(1, decoded.mirrors.size)
        assertEquals("a", decoded.mirrors.single().id)
    }
}
