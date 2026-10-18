package dev.ohmydrift.mirror

internal data class MirrorProbeResult(
    val status: ProbeStatus,
    val latencyMs: Long?,
    val successes: Int,
    val detail: String,
)
