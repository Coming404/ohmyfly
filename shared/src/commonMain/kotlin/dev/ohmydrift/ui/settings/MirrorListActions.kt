package dev.ohmydrift.ui.settings

import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.buildMirrorUrl

internal fun movedMirror(entries: List<MirrorEntry>, id: String, delta: Int): List<MirrorEntry> {
    val from = entries.indexOfFirst { it.id == id }
    val to = from + delta
    if (from < 0 || to !in entries.indices) return entries
    return entries.toMutableList().apply { add(to, removeAt(from)) }
}

internal fun removedMirror(entries: List<MirrorEntry>, id: String): List<MirrorEntry> =
    entries.filterNot { it.id == id }

/** Uses the production rewrite without making a request. */
internal fun mirrorUrlPreview(entry: MirrorEntry, sample: String): String = buildMirrorUrl(entry, sample)
