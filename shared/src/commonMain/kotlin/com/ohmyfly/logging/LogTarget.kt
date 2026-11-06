// URL 的路径、查询和用户信息都可能携带凭据，日志只保留来源主机。
package com.ohmyfly.logging

import io.ktor.http.parseUrl

internal fun logTarget(raw: String): String {
    val url = parseUrl(raw) ?: return "无效地址"
    if (url.host.isBlank() || url.protocol.name !in setOf("http", "https")) return "无效地址"
    val host = if (':' in url.host) "[${url.host}]" else url.host
    return "${url.protocol.name}://$host:${url.port}"
}
