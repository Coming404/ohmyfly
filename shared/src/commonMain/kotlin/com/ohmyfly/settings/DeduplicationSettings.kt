package com.ohmyfly.settings

import com.ohmyfly.catalog.DeduplicationRule
import com.ohmyfly.catalog.catalogJson

internal fun outputDeduplicationOr(raw: String?, fallback: DeduplicationRule): DeduplicationRule {
    if (raw == null) return fallback
    return try {
        catalogJson.decodeFromString(DeduplicationRule.serializer(), raw)
            .takeIf { outputDeduplicationError(it) == null } ?: fallback
    } catch (_: IllegalArgumentException) { fallback }
}

internal fun outputDeduplicationError(rule: DeduplicationRule): String? = when {
    rule.mode !in setOf("none", "common", "protocol") -> "汇总去重模式无效"
    (rule.commonFields + rule.protocolRules.values.flatten()).any { it.isBlank() } -> "去重字段不能为空"
    else -> null
}
