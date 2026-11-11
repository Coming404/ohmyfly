// 独立解析订阅用量响应头，区分缺失字段与合法零值。
// Ported from ZeroDeng01/sublinkPro @34e93e695e73ea34f3a1e14a742e41ff10891a66 (MIT).
// Copyright (c) 2025 ZeroDeng. See third-party/licenses/sublinkPro-MIT.txt.
package com.ohmyfly.subscription

internal const val SubscriptionUsageHeader = "subscription-userinfo"

@kotlinx.serialization.Serializable
internal data class SubscriptionUsage(
    val upload: Long? = null,
    val download: Long? = null,
    val total: Long? = null,
    val expireEpochSeconds: Long? = null,
)

internal fun parseSubscriptionUserInfo(header: String?): SubscriptionUsage? {
    var upload: Long? = null
    var download: Long? = null
    var total: Long? = null
    var expire: Long? = null
    for (field in header.orEmpty().split(';')) {
        val pair = field.trim().split('=', limit = 2)
        if (pair.size != 2) continue
        val value = pair[1].trim().toLongOrNull()?.takeIf { it >= 0 } ?: continue
        when (pair[0].trim()) {
            "upload" -> upload = value
            "download" -> download = value
            "total" -> total = value
            "expire" -> expire = value
        }
    }
    if (upload == null && download == null && total == null && expire == null) return null
    return SubscriptionUsage(upload, download, total, expire)
}
