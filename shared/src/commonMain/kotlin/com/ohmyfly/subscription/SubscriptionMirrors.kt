// 每次订阅操作接收当前镜像设置；带未知请求头的地址不交给公共镜像。
package com.ohmyfly.subscription

import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.mirror.buildMirrorUrl
import com.ohmyfly.mirror.githubResourceError
import com.ohmyfly.mirror.orderedMirrorCandidates
import com.ohmyfly.mirror.validateMirror

internal data class SubscriptionMirrorSettings(
    val enabled: Boolean = false,
    val mirrors: List<MirrorEntry> = emptyList(),
    val selectedMirrorId: String? = null,
)

internal fun subscriptionRequestUrls(
    url: String, headers: Map<String, List<String>>, settings: SubscriptionMirrorSettings,
): List<String> {
    val publicHeaders = headers.keys.all {
        it.lowercase() in setOf("user-agent", "accept", "accept-encoding", "cache-control")
    }
    if (!settings.enabled || !publicHeaders || githubResourceError(url) != null) return listOf(url)
    return orderedMirrorCandidates(true, settings.mirrors, settings.selectedMirrorId).mapNotNull { entry ->
        when {
            entry == null -> url
            validateMirror(entry) == null -> buildMirrorUrl(entry, url)
            else -> null
        }
    }
}
