package com.ohmyfly.ui.settings

import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.mirror.buildMirrorUrl

internal fun removedMirror(entries: List<MirrorEntry>, id: String): List<MirrorEntry> =
    entries.filterNot { it.id == id }

/** Uses the production rewrite without making a request. */
internal fun mirrorUrlPreview(entry: MirrorEntry, sample: String): String = buildMirrorUrl(entry, sample)
