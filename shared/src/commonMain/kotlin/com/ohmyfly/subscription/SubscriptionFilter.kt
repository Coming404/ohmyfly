package com.ohmyfly.subscription

import com.ohmyfly.settings.SubscriptionSettings

// 仅在导入阶段使用当前设置快照，不对已保存节点执行设置规则重算。
internal class SubscriptionFilter(settings: SubscriptionSettings) {
    private val included = pattern(settings.includePattern, "包含")
    private val excluded = pattern(settings.excludePattern, "排除")
    private val protocols = settings.protocols

    fun reason(name: String, server: String, type: String): Int = when {
        isLocalServer(server) -> 0
        included != null && !included.containsMatchIn(name) -> 1
        excluded != null && excluded.containsMatchIn(name) -> 2
        protocols.isNotEmpty() && type !in protocols -> 3
        else -> -1
    }

    private fun pattern(value: String, label: String): Regex? {
        val trimmed = value.trim()
        require(trimmed.length <= 512) { "塔台过滤规则不能超过 512 个字符" }
        return try {
            trimmed.takeIf(String::isNotEmpty)?.let { Regex(it, RegexOption.IGNORE_CASE) }
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("${label}规则不是有效的正则表达式")
        }
    }
}
