package com.ohmyfly.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.mirror.defaultMirrors
import com.ohmyfly.mirror.normalizedMirrorAddress
import com.ohmyfly.mirror.validateMirror

/** 镜像用 count + 每项 index 字段存进 Preferences，不额外引入 JSON。 */
internal object MirrorCodec {
    const val MAX_MIRRORS = 100
    const val DEFAULT_ENABLED = true

    private val countKey = intPreferencesKey("mirror.count")
    private val selectedKey = stringPreferencesKey("mirror.selectedId")
    private val enabledKey = booleanPreferencesKey("mirror.enabled")

    fun write(target: MutablePreferences, settings: AppSettings) {
        val mirrors = settings.mirrors.take(MAX_MIRRORS)
        target[countKey] = mirrors.size
        target[enabledKey] = settings.mirrorEnabled
        val selected = settings.selectedMirrorId
        if (selected != null) target[selectedKey] = selected else target.remove(selectedKey)
        mirrors.forEachIndexed { index, entry ->
            target[idKey(index)] = entry.id
            target[nameKey(index)] = entry.name
            target[addressKey(index)] = entry.address
        }
    }

    /** 损坏或缺失 count 只在内存回退默认镜像，不改写原文件。 */
    fun read(preferences: Preferences): MirrorState {
        val count = preferences.readInt(countKey, -1)
        val enabled = preferences.readBoolean(enabledKey, DEFAULT_ENABLED)
        if (count !in 0..MAX_MIRRORS) return MirrorState(defaultMirrors(), null, enabled)
        val mirrors = (0 until count)
            .mapNotNull { index -> readMirror(preferences, index) }
            .distinctBy { it.id.lowercase() }
            .distinctBy(::normalizedMirrorAddress)
        val selected = preferences.readString(selectedKey)?.trim()?.let { id ->
            mirrors.firstOrNull { it.id.equals(id, ignoreCase = true) }?.id
        }
        return MirrorState(mirrors, selected, enabled)
    }

    private fun readMirror(preferences: Preferences, index: Int): MirrorEntry? {
        val id = preferences.readString(idKey(index))?.trim() ?: return null
        if (!SettingsLimits.isUsableMirrorId(id)) return null
        val address = preferences.readString(addressKey(index))?.trim() ?: return null
        if (address.isEmpty()) return null
        val name = preferences.readString(nameKey(index))?.trim()?.ifEmpty { id } ?: id
        return MirrorEntry(id = id, name = name, address = address).takeIf { validateMirror(it) == null }
    }

    private fun idKey(index: Int) = stringPreferencesKey("mirror.$index.id")
    private fun nameKey(index: Int) = stringPreferencesKey("mirror.$index.name")
    private fun addressKey(index: Int) = stringPreferencesKey("mirror.$index.address")

    private fun Preferences.readInt(key: Preferences.Key<Int>, fallback: Int): Int =
        try { this[key] ?: fallback } catch (_: Exception) { fallback }

    private fun Preferences.readString(key: Preferences.Key<String>): String? =
        try { this[key] } catch (_: Exception) { null }

    private fun Preferences.readBoolean(key: Preferences.Key<Boolean>, fallback: Boolean): Boolean =
        try { this[key] ?: fallback } catch (_: Exception) { fallback }
}
