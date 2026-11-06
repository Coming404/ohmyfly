// 每次跳转仍执行原有安全校验，日志只记录目的主机。
package com.ohmyfly.mirror

import com.ohmyfly.logging.logger
import com.ohmyfly.logging.logTarget

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
        logger.debug { "[镜像跳转] 第 ${hop + 1} 跳：${logTarget(current)}，HTTP ${response.statusCode}" }
        current = response.location?.let { resolveRedirect(current, it) }
            ?: throw MirrorFetchException("重定向目标不被允许")
        logger.debug { "[镜像跳转] 下一目标 ${logTarget(current)}" }
    }
    throw MirrorFetchException("重定向失败")
}
