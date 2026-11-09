package com.ohmyfly.mirror

internal data class MirrorProbeResult(
    val status: ProbeStatus,
    val latencyMs: Long?,
    val detail: String,
)
