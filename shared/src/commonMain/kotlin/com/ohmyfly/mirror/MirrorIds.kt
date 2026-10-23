package com.ohmyfly.mirror

import kotlin.random.Random

/** Collision-checked persisted id; independent of display names and network state. */
internal fun newMirrorId(existing: Set<String>, random: Random = Random.Default): String {
    repeat(16) {
        val candidate = "mirror-" + random.nextInt(16).toString(16) +
            random.nextInt(0x10000).toString(16).padStart(4, '0') +
            random.nextInt(0x10000).toString(16).padStart(4, '0')
        if (existing.none { it.equals(candidate, ignoreCase = true) }) return candidate
    }
    var fallback = existing.size
    while (existing.any { it.equals("mirror-$fallback", ignoreCase = true) }) fallback += 1
    return "mirror-$fallback"
}
