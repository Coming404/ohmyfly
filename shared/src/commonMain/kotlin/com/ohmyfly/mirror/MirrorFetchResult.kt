package com.ohmyfly.mirror

internal data class MirrorFetchResult(
    val response: ProbeResponse,
    val usedMirrorId: String?,
    val failedMirrorIds: List<String>,
)
