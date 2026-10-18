package dev.ohmydrift.mirror

/** Display order only; changing it never changes the selected mirror or the stored list. */
internal enum class MirrorOrder {
    Manual,
    LatencyAscending,
    LatencyDescending,
}
