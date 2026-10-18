package dev.ohmydrift.mirror

/**
 * Display-only ordering. Successful probes go first in the requested direction, everything else
 * stays below in the persisted list order. The input list and the selection are untouched.
 */
internal fun sortMirrors(
    entries: List<MirrorEntry>,
    results: Map<String, MirrorProbeResult>,
    order: MirrorOrder,
): List<MirrorEntry> {
    if (order == MirrorOrder.Manual) return entries
    val (passed, rest) = entries.partition { results[it.id]?.status == ProbeStatus.Success }
    if (passed.isEmpty()) return entries
    // Sorted only over successful rows; ties keep the persisted order via stable sort.
    val comparator = compareBy<MirrorEntry> { results[it.id]?.latencyMs ?: Long.MAX_VALUE }
    val ranked = if (order == MirrorOrder.LatencyDescending) {
        passed.sortedWith(compareByDescending { results[it.id]?.latencyMs ?: Long.MIN_VALUE })
    } else {
        passed.sortedWith(comparator)
    }
    return ranked + rest
}
