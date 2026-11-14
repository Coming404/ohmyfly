// 分组摘要与搜索结果使用同一目录修订；节点归属持久化在原生目录。
package com.ohmyfly.catalog

import kotlinx.serialization.Serializable

@Serializable
internal data class CatalogGroup(val id: Long, val name: String, val count: Long = 0)

@Serializable
internal data class CatalogGroups(
    val revision: Long = 0,
    val groups: List<CatalogGroup> = emptyList(),
    val counts: Map<String, Long> = emptyMap(),
)
