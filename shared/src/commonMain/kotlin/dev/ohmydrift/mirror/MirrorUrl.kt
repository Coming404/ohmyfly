package dev.ohmydrift.mirror

import io.ktor.http.Url

/**
 * Prefix mode appends the original URL unchanged after the base; template mode substitutes
 * the single `{url}` with a percent-encoded original URL. No other rewriting is attempted.
 */
internal fun validateMirror(entry: MirrorEntry): String? {
    if (entry.id.isBlank() || entry.id.equals(DIRECT_MIRROR_ID, true) || entry.id.length > 128 || entry.id.any { it.isWhitespace() || it.isControl() }) return "镜像标识无效"
    if (entry.name.isBlank() || entry.name.length > 64 || entry.name.any { it.isControl() }) return "镜像名称需为 1–64 个可见字符"
    val address = entry.address
    if (address.isBlank()) return "镜像地址不能为空"
    if (address.length > MAX_URL_LENGTH) return "镜像地址过长"
    if (address.any { it.isWhitespace() || it.isControl() }) return "镜像地址不能包含空白或控制字符"
    if (!address.startsWith("https://", ignoreCase = true)) return "镜像地址必须以 https:// 开头"
    if ('@' in address) return "镜像地址不能包含用户名或密码"
    if ('#' in address) return "镜像地址不能包含片段"
    if (occurrences(address, "{url}") > 1) return "模板只能包含一个 {url}"
    when (entry.mode) {
        MirrorMode.Prefix -> {
            if ('?' in address) return "前缀镜像不能包含查询参数"
            if ('{' in address || '}' in address) return "前缀镜像不能包含占位符"
        }
        MirrorMode.Template -> {
            val placeholder = address.indexOf("{url}")
            if (placeholder < 0) return "模板必须包含一个 {url}"
            val authorityEnd = address.substring(8).let { rest ->
                rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it + 8 }
            }
            if (placeholder < authorityEnd) return "模板占位符不能出现在主机名中"
            if (address.replace("{url}", "").any { it == '{' || it == '}' }) return "只支持 {url} 占位符"
            val parameters = runCatching { Url(address.replace("{url}", "probe")).parameters.names() }.getOrNull()
                ?: return "模板网址无效"
            if (parameters.any { it.lowercase() in setOf("token", "access_token", "auth", "authorization", "api_key", "apikey", "password", "signature", "key", "sig") }) return "镜像网址不能保存认证参数"
        }
    }
    parseHttpsUrl(address) ?: return "镜像地址必须是有效的公网 HTTPS 地址"
    return null
}

/** Forgiving normalization for previews, dedupe and storage; never throws on a draft value. */
internal fun normalizedMirrorAddress(entry: MirrorEntry): String {
    val normalized = normalizeHostCase(entry.address.trim())
    return when (entry.mode) {
        MirrorMode.Prefix -> normalized.trimEnd('/') + "/"
        MirrorMode.Template -> normalized
    }
}

internal fun buildMirrorUrl(entry: MirrorEntry, originalUrl: String): String = when (entry.mode) {
    MirrorMode.Prefix -> normalizedMirrorAddress(entry) + originalUrl
    MirrorMode.Template -> normalizedMirrorAddress(entry).replace("{url}", percentEncode(originalUrl))
}

/** Lowercases scheme and authority only; path and query keep their exact case. */
private fun normalizeHostCase(address: String): String {
    val schemeEnd = address.indexOf("://")
    if (schemeEnd <= 0) return address
    val rest = address.substring(schemeEnd + 3)
    val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
        .let { if (it < 0) rest.length else it }
    return address.substring(0, schemeEnd).lowercase() + "://" +
        rest.substring(0, authorityEnd).lowercase().removeSuffix(":443") +
        rest.substring(authorityEnd)
}

internal fun percentEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        if (code < 0x80 && (char.isLetterOrDigit() || char == '-' || char == '.' || char == '_' || char == '~')) {
            append(char)
        } else {
            append('%').append(HEX[code shr 4]).append(HEX[code and 0x0F])
        }
    }
}

private fun occurrences(text: String, needle: String): Int {
    var count = 0
    var index = text.indexOf(needle)
    while (index >= 0) {
        count += 1
        index = text.indexOf(needle, index + needle.length)
    }
    return count
}

private fun Char.isControl(): Boolean = code < 0x20 || code == 0x7F

private const val HEX = "0123456789ABCDEF"
