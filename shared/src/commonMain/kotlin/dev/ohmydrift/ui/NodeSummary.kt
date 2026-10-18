package dev.ohmydrift.ui

/** Public node metadata for lists; never includes connection credentials. */
internal data class NodeSummary(
    val id: String,
    val name: String,
    val region: String,
    val protocol: String,
    val latencyMs: Int? = null,
)
