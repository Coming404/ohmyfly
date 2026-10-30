package com.ohmyfly.mirror

internal data class ProbeResponse(
    val statusCode: Int,
    val body: ByteArray,
    val ttfbMs: Long,
    val location: String? = null,
)
