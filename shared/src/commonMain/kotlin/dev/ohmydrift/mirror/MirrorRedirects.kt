package dev.ohmydrift.mirror

/** Redirect targets are checked again and the platform rechecks every resolved address. */
internal suspend fun fetchWithRedirects(
    transport: MirrorTransport,
    url: String,
    timeoutMillis: Long,
    maxBytes: Int,
): ProbeResponse {
    var current = url
    var elapsed = 0L
    repeat(MAX_REDIRECTS + 1) { hop ->
        val response = transport.fetch(current, timeoutMillis, maxBytes)
        elapsed += response.ttfbMs.coerceAtLeast(0)
        if (response.statusCode !in setOf(301, 302, 303, 307, 308)) return response.copy(ttfbMs = elapsed)
        if (hop == MAX_REDIRECTS) throw MirrorFetchException("重定向次数超过上限")
        current = response.location?.let { resolveRedirect(current, it) }
            ?: throw MirrorFetchException("重定向目标不被允许")
    }
    throw MirrorFetchException("重定向失败")
}
