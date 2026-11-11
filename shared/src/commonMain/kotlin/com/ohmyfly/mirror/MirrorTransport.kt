package com.ohmyfly.mirror

/** HTTP requests without automatic redirects, cookies, authentication, or body caching. */
internal interface MirrorTransport {
    suspend fun head(url: String, timeoutMillis: Long): MirrorHeadResponse
    suspend fun fetch(url: String, timeoutMillis: Long, maxBytes: Int): ProbeResponse
}

internal expect fun createMirrorTransport(): MirrorTransport

/** 与测速共用现有安全客户端，订阅自行保留响应头和自己的字节上限。 */
internal expect fun createMirrorClient(): io.ktor.client.HttpClient

internal const val DIRECT_MIRROR_ID = "direct"
internal const val DEFAULT_TIMEOUT_MILLIS = 5_000L
internal const val DEFAULT_MAX_BYTES = 65_536
internal const val MAX_REDIRECTS = 3
