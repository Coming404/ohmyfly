package dev.ohmydrift.mirror

/** Successful results are always displayed in ascending latency; every other state stays last. */
internal fun sortMirrors(
    entries: List<MirrorEntry>,
    results: Map<String, MirrorProbeResult>,
): List<MirrorEntry> {
    val (passed, rest) = entries.partition { results[it.id]?.status == ProbeStatus.Success }
    if (passed.isEmpty()) return entries
    val ranked = passed.sortedWith(compareBy { results[it.id]?.latencyMs ?: Long.MAX_VALUE })
    return ranked + rest
}

/** Returns the first fastest measured success, preserving the saved order for ties. */
internal fun fastestMirrorId(
    entries: List<MirrorEntry>,
    results: Map<String, MirrorProbeResult>,
): String? = entries.asSequence()
    .mapNotNull { entry ->
        results[entry.id]?.takeIf { it.status == ProbeStatus.Success }
            ?.latencyMs?.let { entry.id to it }
    }
    .minByOrNull { it.second }
    ?.first
