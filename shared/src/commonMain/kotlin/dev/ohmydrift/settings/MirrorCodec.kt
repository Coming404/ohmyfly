package dev.ohmydrift.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.MirrorMode
import dev.ohmydrift.mirror.MirrorOrder
import dev.ohmydrift.mirror.defaultMirrors
import dev.ohmydrift.mirror.normalizedMirrorAddress
import dev.ohmydrift.mirror.validateMirror

/**
 * 镜像用 count + 每项 index 字段存进 Preferences，不额外引入 JSON。
 * 读取时跳过空标识/空地址与重复标识；缺失 count 视为旧文件，返回内置默认镜像。
 */
internal object MirrorCodec {
    const val MAX_MIRRORS = 100

    private val countKey = intPreferencesKey("mirror.count")
    private val orderKey = stringPreferencesKey("mirror.order")
    private val selectedKey = stringPreferencesKey("mirror.selectedId")

    fun write(target: MutablePreferences, settings: AppSettings) {
        val mirrors = settings.mirrors.take(MAX_MIRRORS)
        target[countKey] = mirrors.size
        target[orderKey] = settings.mirrorOrder.name
        val selected = settings.selectedMirrorId
        if (selected == null) {
            if (selectedKey in target) target.remove(selectedKey)
        } else {
            target[selectedKey] = selected
        }
        mirrors.forEachIndexed { index, entry ->
            target[idKey(index)] = entry.id
            target[nameKey(index)] = entry.name
            target[addressKey(index)] = entry.address
            target[modeKey(index)] = entry.mode.name
        }
        for (index in mirrors.size until MAX_MIRRORS) {
            listOf(idKey(index), nameKey(index), addressKey(index), modeKey(index))
                .forEach { key -> if (key in target) target.remove(key) }
        }
    }

    fun read(preferences: Preferences): MirrorState {
        val count = preferences.readInt(countKey, -1)
        val order = preferences.readString(orderKey)
            ?.let { raw -> MirrorOrder.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
            ?: MirrorOrder.LatencyAscending
        if (count !in 0..MAX_MIRRORS) {
            return MirrorState(defaultMirrors(), null, order)
        }
        val mirrors = (0 until count)
            .mapNotNull { index -> readMirror(preferences, index) }
            .distinctBy { it.id.lowercase() }
            .distinctBy { it.mode to normalizedMirrorAddress(it) }
        val selected = preferences.readString(selectedKey)?.trim()?.let { id ->
            mirrors.firstOrNull { it.id.equals(id, ignoreCase = true) }?.id
        }
        return MirrorState(mirrors, selected, order)
    }

    private fun readMirror(preferences: Preferences, index: Int): MirrorEntry? {
        val id = preferences.readString(idKey(index))?.trim() ?: return null
        if (!SettingsLimits.isUsableMirrorId(id)) return null
        val address = preferences.readString(addressKey(index))?.trim() ?: return null
        if (address.isEmpty()) return null
        val name = preferences.readString(nameKey(index))?.trim()?.ifEmpty { id } ?: id
        val mode = preferences.readString(modeKey(index))
            ?.let { raw -> MirrorMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
            ?: MirrorMode.Prefix
        return MirrorEntry(id = id, name = name, address = address, mode = mode)
            .takeIf { validateMirror(it) == null }
    }

    private fun idKey(index: Int) = stringPreferencesKey("mirror.$index.id")
    private fun nameKey(index: Int) = stringPreferencesKey("mirror.$index.name")
    private fun addressKey(index: Int) = stringPreferencesKey("mirror.$index.address")
    private fun modeKey(index: Int) = stringPreferencesKey("mirror.$index.mode")

    private fun Preferences.readInt(key: Preferences.Key<Int>, fallback: Int): Int =
        try {
            this[key] ?: fallback
        } catch (_: Exception) {
            fallback
        }

    private fun Preferences.readString(key: Preferences.Key<String>): String? =
        try {
            this[key]
        } catch (_: Exception) {
            null
        }
}
