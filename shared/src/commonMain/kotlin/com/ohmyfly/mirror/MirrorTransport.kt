package com.ohmyfly.mirror

/** Bounded HTTP response, without redirects, cookies, authentication, or body caching. */
internal interface MirrorTransport {
    suspend fun fetch(url: String, timeoutMillis: Long, maxBytes: Int): ProbeResponse
}

internal expect fun createMirrorTransport(): MirrorTransport

internal const val DIRECT_MIRROR_ID = "direct"
internal const val DEFAULT_TIMEOUT_MILLIS = 5_000L
internal const val DEFAULT_MAX_BYTES = 65_536
internal const val MAX_REDIRECTS = 3
