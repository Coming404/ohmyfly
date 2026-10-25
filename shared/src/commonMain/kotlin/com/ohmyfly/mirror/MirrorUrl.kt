package com.ohmyfly.mirror

/** Prefix mirrors append the original URL unchanged after their base. */
internal fun validateMirror(entry: MirrorEntry): String? {
    if (entry.id.isBlank() || entry.id.equals(DIRECT_MIRROR_ID, true) || entry.id.length > 128 || entry.id.any { it.isWhitespace() || it.isControl() }) return "镜像标识无效"
    if (entry.name.isBlank() || entry.name.length > 64 || entry.name.any { it.isControl() }) return "镜像名称需为 1–64 个可见字符"
    val address = normalizedMirrorInput(entry.address)
    if (address.isBlank()) return "镜像地址不能为空"
    if (address.length > MAX_URL_LENGTH) return "镜像地址过长"
    if (address.any { it.isWhitespace() || it.isControl() }) return "镜像地址不能包含空白或控制字符"
    if (!address.startsWith("https://", ignoreCase = true) && !address.startsWith("http://", ignoreCase = true)) return "仅支持 HTTP 或 HTTPS 地址"
    if ('@' in address) return "镜像地址不能包含用户名或密码"
    if ('#' in address) return "镜像地址不能包含片段"
    if ('?' in address) return "前缀镜像不能包含查询参数"
    if ('{' in address || '}' in address) return "前缀镜像不能包含占位符"
    parseHttpUrl(address) ?: return "请输入有效的公网 HTTP 或 HTTPS 地址"
    return null
}

/** A bare domain defaults to HTTPS. Explicit/unknown schemes are never silently rewritten. */
internal fun normalizedMirrorInput(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return value
    val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    val hostPort = Regex("^[A-Za-z0-9.-]+:[0-9]+(?:/.*)?$")
    return if (scheme.containsMatchIn(value) && !hostPort.matches(value)) value else "https://$value"
}

/** Forgiving normalization for previews, dedupe and storage; never throws on a draft value. */
internal fun normalizedMirrorAddress(entry: MirrorEntry): String =
    normalizeHostCase(normalizedMirrorInput(entry.address)).trimEnd('/') + "/"

internal fun buildMirrorUrl(entry: MirrorEntry, originalUrl: String): String =
    normalizedMirrorAddress(entry) + originalUrl

/** Lowercases scheme and authority only; path and query keep their exact case. */
private fun normalizeHostCase(address: String): String {
    val schemeEnd = address.indexOf("://")
    if (schemeEnd <= 0) return address
    val rest = address.substring(schemeEnd + 3)
    val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
        .let { if (it < 0) rest.length else it }
    val scheme = address.substring(0, schemeEnd).lowercase()
    val authority = rest.substring(0, authorityEnd).lowercase()
        .removeSuffix(if (scheme == "http") ":80" else ":443")
    return scheme + "://" + authority + rest.substring(authorityEnd)
}

private fun Char.isControl(): Boolean = code < 0x20 || code == 0x7F
