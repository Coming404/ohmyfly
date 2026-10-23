package com.ohmyfly.mirror

/** Applied to literal hosts and to every address returned by the resolver. */
internal fun isPublicAddress(bytes: ByteArray): Boolean {
    val b = bytes.map { it.toInt() and 255 }
    if (b.size == 4) {
        val a = b[0]
        val second = b[1]
        return !(a == 0 || a == 10 || a == 127 || a >= 224 ||
            (a == 100 && second in 64..127) || (a == 169 && second == 254) ||
            (a == 172 && second in 16..31) || (a == 192 && second in setOf(0, 168)) ||
            (a == 198 && second in 18..19) || (a == 198 && second == 51 && b[2] == 100) ||
            (a == 203 && second == 0 && b[2] == 113) ||
            b == listOf(168, 63, 129, 16))
    }
    if (b.size != 16) return false
    if (b.take(10).all { it == 0 } && b[10] == 255 && b[11] == 255) {
        return isPublicAddress(bytes.copyOfRange(12, 16))
    }
    // Well-known /96 DNS64 prefix; do not admit a translated private IPv4 peer.
    if (b.take(12) == listOf(0, 100, 255, 155, 0, 0, 0, 0, 0, 0, 0, 0)) {
        return isPublicAddress(bytes.copyOfRange(12, 16))
    }
    if (b[0] !in 0x20..0x3f) return false
    if (b[0] == 0x20 && b[1] == 0x02) return false // 6to4 can encode private IPv4.
    if (b.take(4) == listOf(0x20, 1, 0, 0)) return false // Teredo.
    return b.take(4) != listOf(0x20, 1, 0x0d, 0xb8)
}

internal fun ipv6Bytes(host: String): ByteArray? {
    if ('%' in host || host.count { it == ':' } < 2) return null
    var text = host.lowercase()
    if ('.' in text) {
        val tail = text.substringAfterLast(':').split('.').map { it.toIntOrNull() ?: return null }
        if (tail.size != 4 || tail.any { it !in 0..255 }) return null
        text = text.substringBeforeLast(':') + ":" + ((tail[0] shl 8) + tail[1]).toString(16) +
            ":" + ((tail[2] shl 8) + tail[3]).toString(16)
    }
    val halves = text.split("::")
    if (halves.size > 2) return null
    fun groups(part: String): List<String> = if (part.isEmpty()) emptyList() else part.split(':')
    val left = groups(halves[0])
    val right = if (halves.size == 2) groups(halves[1]) else emptyList()
    val missing = 8 - left.size - right.size
    if (missing < 0 || (halves.size == 1 && missing != 0) || (halves.size == 2 && missing < 1)) return null
    val expanded = left + List(missing) { "0" } + right
    val result = ByteArray(16)
    expanded.forEachIndexed { index, group ->
        if (group.length !in 1..4 || group.any { it !in "0123456789abcdef" }) return null
        val value = group.toIntOrNull(16) ?: return null
        result[index * 2] = (value shr 8).toByte()
        result[index * 2 + 1] = value.toByte()
    }
    return result
}
