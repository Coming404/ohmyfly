// GitHub 域名分类供订阅 UA 与公开资源候选共用，不等同于重定向安全白名单。
package com.ohmyfly.mirror

internal fun isGitHubHost(host: String): Boolean {
    val normalized = host.lowercase().trimEnd('.')
    return listOf("github.com", "githubusercontent.com").any {
        normalized == it || normalized.endsWith(".$it")
    }
}

// 原始资源是否允许公开转发仍由 githubResourceError 单独验证。
internal fun orderedMirrorCandidates(
    enabled: Boolean, mirrors: List<MirrorEntry>, selectedMirrorId: String?,
): List<MirrorEntry?> {
    if (!enabled) return listOf(null)
    val selectedIndex = mirrors.indexOfFirst { it.id == selectedMirrorId }
    return buildList<MirrorEntry?> {
        if (selectedIndex >= 0) add(mirrors[selectedIndex])
        mirrors.forEachIndexed { index, entry -> if (index != selectedIndex) add(entry) }
        add(null)
    }.distinctBy { it?.id ?: DIRECT_MIRROR_ID }
}
