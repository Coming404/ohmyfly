package dev.ohmydrift.ui.settings

import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.buildMirrorUrl

internal fun removedMirror(entries: List<MirrorEntry>, id: String): List<MirrorEntry> =
    entries.filterNot { it.id == id }

/** Uses the production rewrite without making a request. */
internal fun mirrorUrlPreview(entry: MirrorEntry, sample: String): String = buildMirrorUrl(entry, sample)
