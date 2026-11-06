// 日志策略与既有 DataStore 共存；损坏字段仅回落内存默认值。
package com.ohmyfly.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ohmyfly.logging.LogLevel
import com.ohmyfly.logging.LogPolicy

internal object LogSettingsCodec {
    private val file = longPreferencesKey("logging.fileMiB")
    private val total = longPreferencesKey("logging.totalMiB")
    private val days = longPreferencesKey("logging.retentionDays")
    private val level = stringPreferencesKey("logging.level")

    fun write(target: MutablePreferences, policy: LogPolicy) {
        target[file] = policy.fileMiB
        target[total] = policy.totalMiB
        target[days] = policy.retentionDays
        target[level] = policy.level.name
    }

    fun read(preferences: Preferences): LogPolicy {
        val defaults = LogPolicy()
        val fileMiB = preferences.longOr(file, defaults.fileMiB, Long.MAX_VALUE / MiB)
        // 两个数值分别合法但相互矛盾时，保证能容纳一个完整文件。
        val totalMiB = preferences.longOr(total, defaults.totalMiB, Long.MAX_VALUE / MiB).coerceAtLeast(fileMiB)
        val rawLevel = runCatching { preferences[level] }.getOrNull()
        return LogPolicy(
            fileMiB = fileMiB, totalMiB = totalMiB,
            retentionDays = preferences.longOr(days, defaults.retentionDays, Long.MAX_VALUE / DayMillis),
            level = LogLevel.entries.firstOrNull { it.name == rawLevel } ?: defaults.level,
        )
    }

    private fun Preferences.longOr(key: Preferences.Key<Long>, default: Long, max: Long): Long =
        runCatching { this[key] }.getOrNull()?.takeIf { it in 1..max } ?: default
}

internal const val MiB = 1024L * 1024
internal const val DayMillis = 86_400_000L

internal fun validateLogPolicy(policy: LogPolicy): String? = when {
    policy.fileMiB !in 1..Long.MAX_VALUE / MiB -> "单文件大小必须是可存储的正整数 MiB"
    policy.totalMiB !in 1..Long.MAX_VALUE / MiB -> "总文件大小必须是可存储的正整数 MiB"
    policy.totalMiB < policy.fileMiB -> "总文件大小不能小于单文件大小"
    policy.retentionDays !in 1..Long.MAX_VALUE / DayMillis -> "保留时间必须是可存储的正整数天数"
    else -> null
}
