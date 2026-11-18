// 解析同次订阅响应的名称头与正文备注；无效标题不影响节点导入。
package com.ohmyfly.subscription

import io.ktor.http.ContentDisposition
import kotlin.io.encoding.Base64

/** 名称只取最终选中的根响应；头部优先，正文 REMARKS 作为无名称头时的补充。 */
internal fun parseSubscriptionName(profileTitle: String?, contentDisposition: String?, body: String = ""): String? =
    parseSubscriptionHeaderName(profileTitle, contentDisposition) ?: parseSubscriptionBodyName(body)

/** 保留已有 profile-title 与 Content-Disposition 的优先级。 */
private fun parseSubscriptionHeaderName(profileTitle: String?, contentDisposition: String?): String? {
    profileTitle?.let { raw ->
        val title = validSubscriptionName(raw) ?: return@let
        val decoded = if (title.startsWith("base64:")) {
            try {
                Base64.decode(title.removePrefix("base64:")).decodeToString(throwOnInvalidSequence = true)
            } catch (_: Exception) { null }
        } else raw
        validSubscriptionName(decoded)?.let { return it }
    }
    if (contentDisposition.isNullOrBlank() || !validDispositionSyntax(contentDisposition)) return null
    val disposition = try {
        ContentDisposition.parse(contentDisposition)
    } catch (_: Exception) { return null }
    val extended = disposition.parameter(ContentDisposition.Parameters.FileNameAsterisk)
    decodeExtendedFilename(extended)?.let { return it }
    return validSubscriptionName(disposition.parameter(ContentDisposition.Parameters.FileName))
}

/** REMARKS 是链接列表的前导元数据，只读取有界前缀，不把节点备注或 YAML 字段当成订阅名称。 */
private fun parseSubscriptionBodyName(body: String): String? {
    val prefix = body.take(8192).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
    if (prefix.isEmpty()) return null
    val text = if (prefix.startsWith("REMARKS=", ignoreCase = true)) prefix else {
        val encoded = prefix.filterNot(Char::isWhitespace).replace('-', '+').replace('_', '/')
        if (encoded.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it !in "+/=" }) return null
        // 长订阅只解码完整四字符块；短订阅接受省略末尾填充的 Base64。
        val complete = if (body.length > 8192) encoded.take(encoded.length / 4 * 4)
            else encoded.padEnd((encoded.length + 3) / 4 * 4, '=')
        try { Base64.decode(complete).decodeToString() } catch (_: IllegalArgumentException) { return null }
    }
    if (body.length > 8192 && '\n' !in text) return null
    val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }
        ?.trimStart('\uFEFF', ' ', '\t')?.removeSuffix("\r") ?: return null
    if (!firstLine.startsWith("REMARKS=", ignoreCase = true)) return null
    return validSubscriptionName(firstLine.substringAfter('='))
}

/** 头部和正文补名使用同一字符有效性规则。 */
private fun validSubscriptionName(value: String?): String? {
    if (value == null || value.any { it.code in 0..31 || it.code in 127..159 || it == '\uFFFD' }) return null
    try {
        value.encodeToByteArray(throwOnInvalidSequence = true)
    } catch (_: Exception) { return null }
    return value.trim().takeIf(String::isNotEmpty)
}

private fun decodeExtendedFilename(value: String?): String? {
    val parts = value?.split('\'', limit = 3) ?: return null
    if (parts.size != 3 || !parts[0].equals("UTF-8", ignoreCase = true)) return null
    val encoded = parts[2]
    val bytes = ByteArray(encoded.length)
    var index = 0
    var count = 0
    while (index < encoded.length) {
        val char = encoded[index]
        if (char == '%') {
            if (index + 2 >= encoded.length) return null
            val hex = encoded.substring(index + 1, index + 3)
            if (hex.any { it !in "0123456789abcdefABCDEF" }) return null
            bytes[count++] = hex.toInt(16).toByte()
            index += 3
        } else {
            if (char !in 'a'..'z' && char !in 'A'..'Z' && char !in '0'..'9' && char !in "!#$&+-.^_`|~") return null
            bytes[count++] = char.code.toByte()
            index++
        }
    }
    return try {
        validSubscriptionName(bytes.decodeToString(endIndex = count, throwOnInvalidSequence = true))
    } catch (_: Exception) { null }
}

// Ktor 对未闭合的引号采取宽松解析；补名入口先拒绝这类损坏元数据。
private fun validDispositionSyntax(value: String): Boolean {
    var quoted = false
    var escaped = false
    for (char in value) {
        if (char.code in 0..31 || char.code in 127..159) return false
        if (escaped) escaped = false
        else if (quoted && char == '\\') escaped = true
        else if (char == '"') quoted = !quoted
    }
    return !quoted && !escaped
}
