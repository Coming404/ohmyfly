package com.ohmyfly.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ohmyfly.ui.ThemeMode
import kotlin.enums.enumEntries

/**
 * Preferences 的纯编解码，不访问磁盘，commonTest 可直接验证。
 * 已知字段使用强类型 key；镜像由 [MirrorCodec] 负责。解码只回落内存默认值，绝不写回。
 */
internal object SettingsCodec {
    const val CURRENT_SCHEMA_VERSION = 1

    private val schema = intPreferencesKey("schema.version")
    private val theme = stringPreferencesKey("ui.themeMode")
    private val materialMode = stringPreferencesKey("ui.materialMode")
    private val reduceTransparency = booleanPreferencesKey("ui.reduceTransparency")
    private val reduceMotion = booleanPreferencesKey("ui.reduceMotion")
    private val hapticEnabled = booleanPreferencesKey("ui.hapticEnabled")
    private val hapticLevel = stringPreferencesKey("ui.hapticLevel")
    private val speedMode = stringPreferencesKey("speed.mode")
    private val latencyUrl = stringPreferencesKey("speed.latencyUrl")
    private val downloadUrl = stringPreferencesKey("speed.downloadUrl")
    private val downloadMiB = intPreferencesKey("speed.downloadMiB")
    private val uploadMiB = intPreferencesKey("speed.uploadMiB")
    private val transferTimeout = intPreferencesKey("speed.transferTimeoutSeconds")
    private val latencyConcurrency = intPreferencesKey("speed.latencyConcurrency")
    private val latencyTimeout = intPreferencesKey("speed.latencyTimeoutSeconds")
    private val latencySamples = intPreferencesKey("speed.latencySamples")
    private val connectionsPerNode = intPreferencesKey("speed.connectionsPerNode")
    private val cellularConfirm = booleanPreferencesKey("speed.cellularConfirm")
    private val maxLatencyMs = intPreferencesKey("speed.maxLatencyMs")
    private val maxFailurePercent = intPreferencesKey("speed.maxFailurePercent")
    private val minDownloadMiBps = doublePreferencesKey("speed.minDownloadMiBps")
    private val minUploadMiBps = doublePreferencesKey("speed.minUploadMiBps")
    private val stopAfter = intPreferencesKey("speed.stopAfter")
    private val renameExport = booleanPreferencesKey("speed.renameExport")
    private val mediaEnabled = booleanPreferencesKey("media.enabled")
    private val mediaPlatforms = stringSetPreferencesKey("media.platforms")
    private val mediaConcurrency = intPreferencesKey("media.concurrency")
    private val mediaTimeout = intPreferencesKey("media.timeoutSeconds")
    private val mediaRetries = intPreferencesKey("media.retries")
    private val subTimeout = intPreferencesKey("subscription.timeoutSeconds")
    private val subRetries = intPreferencesKey("subscription.retries")
    private val subMaxMiB = intPreferencesKey("subscription.maxMiB")
    private val subUserAgent = stringPreferencesKey("subscription.userAgent")
    private val subInclude = stringPreferencesKey("subscription.includePattern")
    private val subExclude = stringPreferencesKey("subscription.excludePattern")
    private val subProtocols = stringSetPreferencesKey("subscription.protocols")

    fun encode(settings: AppSettings): MutablePreferences = mutablePreferencesOf().also { write(it, settings) }

    /** 写入当前 schema，移除未知及过时字段。 */
    fun write(target: MutablePreferences, settings: AppSettings) {
        target.clear()
        target[schema] = CURRENT_SCHEMA_VERSION
        target[theme] = settings.themeMode.name
        target[materialMode] = settings.materialMode.name
        target[reduceTransparency] = settings.reduceTransparency
        target[reduceMotion] = settings.reduceMotion
        target[hapticEnabled] = settings.hapticEnabled
        target[hapticLevel] = settings.hapticLevel.name
        val speed = settings.speed
        target[speedMode] = speed.mode.name
        target[latencyUrl] = speed.latencyUrl
        target[downloadUrl] = speed.downloadUrl
        target[downloadMiB] = speed.downloadMiB
        target[uploadMiB] = speed.uploadMiB
        target[transferTimeout] = speed.transferTimeoutSeconds
        target[latencyConcurrency] = speed.latencyConcurrency
        target[latencyTimeout] = speed.latencyTimeoutSeconds
        target[latencySamples] = speed.latencySamples
        target[connectionsPerNode] = speed.connectionsPerNode
        target[cellularConfirm] = speed.cellularConfirm
        target[maxLatencyMs] = speed.maxLatencyMs
        target[maxFailurePercent] = speed.maxFailurePercent
        target[minDownloadMiBps] = speed.minDownloadMiBps
        target[minUploadMiBps] = speed.minUploadMiBps
        target[stopAfter] = speed.stopAfter
        target[renameExport] = speed.renameExport
        val media = settings.media
        target[mediaEnabled] = media.enabled
        target[mediaPlatforms] = media.platforms
        target[mediaConcurrency] = media.concurrency
        target[mediaTimeout] = media.timeoutSeconds
        target[mediaRetries] = media.retries
        val subscription = settings.subscription
        target[subTimeout] = subscription.timeoutSeconds
        target[subRetries] = subscription.retries
        target[subMaxMiB] = subscription.maxMiB
        target[subUserAgent] = subscription.userAgent
        target[subInclude] = subscription.includePattern
        target[subExclude] = subscription.excludePattern
        target[subProtocols] = subscription.protocols
        MirrorCodec.write(target, settings)
    }

    /** 未来版本明确抛错，由仓库上抛；其余损坏字段只回落，不修改原文件。 */
    fun decode(preferences: Preferences): AppSettings {
        val version = preferences.read(schema, 0)
        if (version > CURRENT_SCHEMA_VERSION) throw SettingsSchemaException(version, CURRENT_SCHEMA_VERSION)
        val defaults = AppSettings()
        val speed = defaults.speed
        val media = defaults.media
        val subscription = defaults.subscription
        val mirrors = MirrorCodec.read(preferences)
        return AppSettings(
            themeMode = preferences.readOrNull(theme).toEnumOr(ThemeMode.System),
            materialMode = preferences.readOrNull(materialMode).toEnumOr(defaults.materialMode),
            reduceTransparency = preferences.read(reduceTransparency, defaults.reduceTransparency),
            reduceMotion = preferences.read(reduceMotion, defaults.reduceMotion),
            hapticEnabled = preferences.read(hapticEnabled, defaults.hapticEnabled),
            hapticLevel = preferences.readOrNull(hapticLevel).toEnumOr(defaults.hapticLevel),
            // 启用开关由 MirrorCodec 独占 `mirror.enabled`，避免两个 Codec 各读一份。
            mirrorEnabled = mirrors.enabled,
            speed = SpeedSettings(
                mode = preferences.readOrNull(speedMode).toEnumOr(speed.mode),
                latencyUrl = SettingsLimits.urlOr(preferences.readOrNull(latencyUrl) ?: speed.latencyUrl, speed.latencyUrl),
                downloadUrl = SettingsLimits.urlOr(preferences.readOrNull(downloadUrl) ?: speed.downloadUrl, speed.downloadUrl),
                downloadMiB = preferences.read(downloadMiB, speed.downloadMiB).inOr(SettingsLimits.downloadMiB, speed.downloadMiB),
                uploadMiB = preferences.read(uploadMiB, speed.uploadMiB).inOr(SettingsLimits.uploadMiB, speed.uploadMiB),
                transferTimeoutSeconds = preferences.read(transferTimeout, speed.transferTimeoutSeconds)
                    .inOr(SettingsLimits.transferTimeoutSeconds, speed.transferTimeoutSeconds),
                latencyConcurrency = preferences.read(latencyConcurrency, speed.latencyConcurrency)
                    .inOr(SettingsLimits.latencyConcurrency, speed.latencyConcurrency),
                latencyTimeoutSeconds = preferences.read(latencyTimeout, speed.latencyTimeoutSeconds)
                    .inOr(SettingsLimits.latencyTimeoutSeconds, speed.latencyTimeoutSeconds),
                latencySamples = preferences.read(latencySamples, speed.latencySamples)
                    .inOr(SettingsLimits.latencySamples, speed.latencySamples),
                connectionsPerNode = preferences.read(connectionsPerNode, speed.connectionsPerNode)
                    .inOr(SettingsLimits.connectionsPerNode, speed.connectionsPerNode),
                cellularConfirm = preferences.read(cellularConfirm, speed.cellularConfirm),
                maxLatencyMs = preferences.read(maxLatencyMs, speed.maxLatencyMs)
                    .inOr(SettingsLimits.maxLatencyMs, speed.maxLatencyMs),
                maxFailurePercent = preferences.read(maxFailurePercent, speed.maxFailurePercent)
                    .inOr(SettingsLimits.maxFailurePercent, speed.maxFailurePercent),
                minDownloadMiBps = preferences.read(minDownloadMiBps, speed.minDownloadMiBps)
                    .inOr(SettingsLimits.minSpeedMiBps, speed.minDownloadMiBps),
                minUploadMiBps = preferences.read(minUploadMiBps, speed.minUploadMiBps)
                    .inOr(SettingsLimits.minSpeedMiBps, speed.minUploadMiBps),
                stopAfter = preferences.read(stopAfter, speed.stopAfter).inOr(SettingsLimits.stopAfter, speed.stopAfter),
                renameExport = preferences.read(renameExport, speed.renameExport),
            ),
            media = MediaSettings(
                enabled = preferences.read(mediaEnabled, media.enabled),
                platforms = sanitizePlatforms(preferences.readOrNull(mediaPlatforms), media.platforms),
                concurrency = preferences.read(mediaConcurrency, media.concurrency)
                    .inOr(SettingsLimits.mediaConcurrency, media.concurrency),
                timeoutSeconds = preferences.read(mediaTimeout, media.timeoutSeconds)
                    .inOr(SettingsLimits.mediaTimeoutSeconds, media.timeoutSeconds),
                retries = preferences.read(mediaRetries, media.retries).inOr(SettingsLimits.mediaRetries, media.retries),
            ),
            subscription = SubscriptionSettings(
                timeoutSeconds = preferences.read(subTimeout, subscription.timeoutSeconds)
                    .inOr(SettingsLimits.subscriptionTimeoutSeconds, subscription.timeoutSeconds),
                retries = preferences.read(subRetries, subscription.retries)
                    .inOr(SettingsLimits.subscriptionRetries, subscription.retries),
                maxMiB = preferences.read(subMaxMiB, subscription.maxMiB)
                    .inOr(SettingsLimits.subscriptionMaxMiB, subscription.maxMiB),
                userAgent = SettingsLimits.userAgentOr(preferences.readOrNull(subUserAgent) ?: subscription.userAgent),
                includePattern = SettingsLimits.patternOr(preferences.readOrNull(subInclude) ?: subscription.includePattern),
                excludePattern = SettingsLimits.patternOr(preferences.readOrNull(subExclude) ?: subscription.excludePattern),
                // 空集合代表“全部支持协议”，只清洗已有条目，不整集合覆盖。
                protocols = SettingsLimits.sanitizeSet(
                    preferences.readOrNull(subProtocols) ?: subscription.protocols,
                    SettingsLimits.MAX_PROTOCOLS,
                ),
            ),
            mirrors = mirrors.mirrors,
            selectedMirrorId = mirrors.selectedMirrorId,
        )
    }

    private fun Int.inOr(range: IntRange, fallback: Int): Int = SettingsLimits.intOr(this, range, fallback)

    private fun Double.inOr(range: ClosedFloatingPointRange<Double>, fallback: Double): Double =
        if (isFinite() && this in range) this else fallback

    /** 存储集合为空代表用户清空；条目全部无效时才回落到默认平台，避免把损坏数据当选择。 */
    private fun sanitizePlatforms(raw: Set<String>?, defaults: Set<String>): Set<String> {
        if (raw == null) return defaults
        val cleaned = SettingsLimits.sanitizeSet(raw, SettingsLimits.MAX_PLATFORMS)
        return if (cleaned.isEmpty() && raw.isNotEmpty()) defaults else cleaned
    }

    private inline fun <reified E : Enum<E>> String?.toEnumOr(fallback: E): E =
        this?.let { raw -> enumEntries<E>().firstOrNull { it.name.equals(raw, ignoreCase = true) } } ?: fallback

    private inline fun <reified T> Preferences.read(key: Preferences.Key<T>, fallback: T): T =
        readOrNull(key) ?: fallback

    private inline fun <reified T> Preferences.readOrNull(key: Preferences.Key<T>): T? =
        try {
            val value: Any? = this[key]
            value as? T
        } catch (_: Exception) {
            null
        }
}
