package dev.ohmydrift.mirror

/**
 * Parses only absolute HTTPS URLs with a public host. Returns null for anything else;
 * user-visible validation layers add their own Chinese explanations.
 */
internal data class HttpsUrl(val host: String, val port: Int, val path: String)

internal fun parseHttpsUrl(raw: String): HttpsUrl? {
    if (raw.length !in 9..MAX_URL_LENGTH) return null
    if (raw.any { it.isWhitespace() || it.isControl() }) return null
    if (!raw.startsWith("https://", ignoreCase = true)) return null
    if ('@' in raw || '#' in raw) return null
    val rest = raw.substring(8)
    val authority = rest.substringBefore('/').substringBefore('?')
    if (authority.isEmpty()) return null
    val host: String
    val port: Int
    if (authority.startsWith("[")) {
        val end = authority.indexOf(']')
        if (end <= 1) return null
        host = authority.substring(1, end).lowercase()
        val tail = authority.substring(end + 1)
        port = when {
            tail.isEmpty() -> 443
            tail.startsWith(":") -> tail.substring(1).toIntOrNull() ?: return null
            else -> return null
        }
    } else {
        host = authority.substringBefore(':').lowercase()
        val rawPort = authority.substringAfter(':', "")
        port = if (':' in authority) rawPort.toIntOrNull() ?: return null else 443
    }
    if (port !in 1..65535) return null
    if (!isPublicHost(host)) return null
    return HttpsUrl(host, port, rest.substring(authority.length))
}

internal fun validatePublicHttps(raw: String): String? = when {
    !raw.startsWith("https://", ignoreCase = true) -> "镜像地址必须以 https:// 开头"
    '@' in raw -> "镜像地址不能包含用户名或密码"
    parseHttpsUrl(raw) == null -> "镜像地址必须是有效的公网 HTTPS 地址"
    else -> null
}

/** Public origin hosts a mirror request may start from; redirects are narrower. */
private val GITHUB_ORIGINS = setOf("github.com", "raw.githubusercontent.com", "codeload.github.com")

/** GitHub download/CDN hosts, including the object storage release assets redirect to. */
private val GITHUB_CDN = GITHUB_ORIGINS + setOf(
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
    "github-releases.githubusercontent.com",
)

internal fun isAllowedGitHubHost(host: String): Boolean = host.lowercase() in GITHUB_CDN

/**
 * A URL that may be rewritten through a public mirror: HTTPS, default port, no query,
 * no userinfo, and an explicit repository path on a GitHub origin host.
 */
internal fun githubResourceError(raw: String): String? {
    if (raw.length > MAX_URL_LENGTH) return "地址过长"
    if (raw.any { it.isWhitespace() || it.isControl() }) return "地址不能包含空白或控制字符"
    if (!raw.startsWith("https://", ignoreCase = true)) return "只支持 HTTPS 的公开 GitHub 地址"
    if ('@' in raw) return "地址不能包含凭据"
    if ('#' in raw) return "地址不能包含片段"
    if ('?' in raw) return "带查询参数的地址不会默认转发到镜像"
    val parsed = parseHttpsUrl(raw) ?: return "地址必须是公开可解析的 HTTPS 地址"
    if (parsed.port != 443) return "只支持 GitHub 的默认 HTTPS 端口"
    if (parsed.host !in GITHUB_ORIGINS) return "只支持 GitHub 公开文件地址"
    val segments = parsed.path.split('/').filter { it.isNotEmpty() }
    if (segments.any { it == "." || it == ".." }) return "地址不能包含路径跳转"
    val concrete = when (parsed.host) {
        "raw.githubusercontent.com", "codeload.github.com" -> segments.size >= 4
        else -> segments.size >= 4 && when (segments[2]) {
            "raw", "archive" -> true
            "releases" -> segments.size >= 6 && segments[3] == "download"
            else -> false
        }
    }
    return if (concrete) null else "地址不是受支持的 GitHub 公开资源路径"
}

/**
 * Follows a redirect only back into GitHub's own HTTPS hosts on the default port.
 * Returning null means the caller must surface the 3xx instead of forwarding it.
 */
internal fun resolveRedirect(currentUrl: String, location: String): String? {
    val absolute = resolveRelative(currentUrl, location.trim()) ?: return null
    val parsed = parseHttpsUrl(absolute) ?: return null
    val current = parseHttpsUrl(currentUrl) ?: return null
    val sameOrigin = parsed.host == current.host && parsed.port == current.port
    if (!sameOrigin && (parsed.port != 443 || !isAllowedGitHubHost(parsed.host))) return null
    return absolute
}

private fun resolveRelative(base: String, location: String): String? {
    if (location.isEmpty()) return null
    if (location.startsWith("https://", ignoreCase = true)) return location
    if (location.startsWith("//")) return "https:$location"
    if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(location)) return null
    val bare = base.substringBefore('#')
    val authorEnd = bare.indexOf('/', 8).let { if (it < 0) bare.length else it }
    val prefix = bare.substring(0, authorEnd)
    return when {
        location.startsWith("/") -> prefix + location
        location.startsWith("?") -> bare.substringBefore('?') + location
        else -> bare.substringBefore('?').substringBeforeLast('/', prefix) + "/" + location
    }
}

private fun isPublicHost(host: String): Boolean {
    if (host.isEmpty() || host.length > 253) return false
    if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
        host.endsWith(".internal") || host.endsWith(".home.arpa") || host.endsWith(".lan")
    ) return false
    if (':' in host) return isPublicIpv6(host)
    if (isIpv4Literal(host)) return isPublicIpv4(host)
    if (!host.contains('.')) return false
    return host.split('.').all { label ->
        label.isNotEmpty() && label.length <= 63 &&
            label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
            label.all { it.isLetterOrDigit() || it == '-' } && label.all { it.code < 0x80 }
    }
}

private fun isIpv4Literal(host: String): Boolean {
    val parts = host.split('.')
    return parts.size == 4 && parts.all { part -> part.isNotEmpty() && part.all { it.isDigit() } }
}

private fun isPublicIpv4(host: String): Boolean {
    val parts = host.split('.').map { it.toIntOrNull() ?: return false }
    return parts.size == 4 && parts.all { it in 0..255 } && isPublicAddress(parts.map(Int::toByte).toByteArray())
}

private fun isPublicIpv6(host: String): Boolean = ipv6Bytes(host)?.let(::isPublicAddress) == true

private fun Char.isControl(): Boolean = code < 0x20 || code == 0x7F

internal const val MAX_URL_LENGTH = 2048
