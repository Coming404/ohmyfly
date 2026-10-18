package dev.ohmydrift.settings

import io.ktor.http.Url

/**
 * 字段合法范围与解码回落的唯一来源，避免编解码和校验各写一套数值。
 * 解码只回落内存值，不写回文件；未来版本迁移时才能按新范围重新解释。
 */
internal object SettingsLimits {
    val downloadMiB = 1..500
    val uploadMiB = 1..100
    val transferTimeoutSeconds = 3..60
    val latencyConcurrency = 1..32
    val latencyTimeoutSeconds = 1..15
    val latencySamples = 1..10
    val connectionsPerNode = 1..8
    val maxLatencyMs = 0..60_000
    val maxFailurePercent = 0..100
    val minSpeedMiBps = 0.0..10_000.0
    val stopAfter = 0..100_000
    val mediaConcurrency = 1..8
    val mediaTimeoutSeconds = 5..30
    val mediaRetries = 0..2
    val subscriptionTimeoutSeconds = 3..120
    val subscriptionRetries = 0..5
    val subscriptionMaxMiB = 1..50
    const val MAX_PLATFORMS = 32
    const val MAX_PROTOCOLS = 64
    const val MAX_PATTERN_LENGTH = 512
    const val MAX_USER_AGENT_LENGTH = 256

    private val credentialQueryKeys = setOf(
        "token", "access_token", "api_key", "apikey", "key", "secret",
        "password", "passwd", "auth", "authorization", "sig", "signature", "credential",
    )

    fun intOr(value: Int, range: IntRange, fallback: Int): Int = if (value in range) value else fallback

    fun urlOr(value: String, fallback: String): String = if (isValidTarget(value)) value else fallback

    fun userAgentOr(value: String): String =
        if (value.length <= MAX_USER_AGENT_LENGTH && value.none { it.isControl() }) value else ""

    fun patternOr(value: String): String =
        if (value.length <= MAX_PATTERN_LENGTH && isValidRegex(value)) value else ""

    fun sanitizeSet(values: Set<String>, limit: Int): Set<String> =
        values.mapNotNull { it.sanitizeToken() }.distinct().take(limit).toSet()

    fun isValidRegex(value: String): Boolean =
        try {
            Regex(value)
            true
        } catch (_: IllegalArgumentException) {
            false
        }

    /** HTTPS target with no user info, fragments, or credential-bearing query keys. */
    fun isValidTarget(value: String): Boolean {
        if (value.isEmpty() || value.length > 2048) return false
        if (value.any { it.isWhitespace() || it.isControl() } || '@' in value || '#' in value) return false
        if (!value.startsWith("https://", ignoreCase = true)) return false
        val authority = value.substring(8).substringBefore('/').substringBefore('?')
        if (authority.isBlank() || authority.startsWith(':')) return false
        val parsed = runCatching { Url(value) }.getOrNull() ?: return false
        if (parsed.host.isBlank() || parsed.user != null || parsed.password != null || parsed.port !in 1..65535) return false
        return parsed.parameters.names().none { it.lowercase() in credentialQueryKeys }
    }

    /** 平台/协议标识：去空白、转小写；含空白、控制字符或超长时丢弃该条。 */
    fun String.sanitizeToken(): String? {
        val token = trim().lowercase()
        if (token.isEmpty() || token.length > 32 || token.any { it.isWhitespace() || it.isControl() }) return null
        return token
    }

    /** 镜像标识：非空、非 direct、且不含空白或控制字符；比较时忽略大小写。 */
    fun isUsableMirrorId(raw: String): Boolean {
        val id = raw.trim()
        return id.isNotEmpty() && !id.equals("direct", ignoreCase = true) && id.none { it.isWhitespace() || it.isControl() }
    }
}

internal fun Int.isControl(): Boolean = this in 0..0x1F || this == 0x7F

internal fun Char.isControl(): Boolean = code.isControl()
