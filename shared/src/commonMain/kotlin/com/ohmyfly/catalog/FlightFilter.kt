package com.ohmyfly.catalog

internal data class FlightFilter(
    val enabled: Boolean = true,
    val search: String = "",
    val includePattern: String = "",
    val excludePattern: String = "",
    val protocols: Set<String> = emptySet(),
) {
    val active: Boolean get() = enabled &&
        (includePattern.isNotBlank() || excludePattern.isNotBlank() || protocols.isNotEmpty())
}

internal class FlightPredicate(private val filter: FlightFilter) {
    private fun compile(pattern: String): Regex? {
        if (!filter.enabled) return null
        require(pattern.length <= 512) { "名称表达式最多 512 个字符" }
        return pattern.trim().takeIf(String::isNotEmpty)?.let { Regex(it, RegexOption.IGNORE_CASE) }
    }
    private val include = compile(filter.includePattern)
    private val exclude = compile(filter.excludePattern)

    fun matches(name: String, type: String): Boolean = name.contains(filter.search.trim(), ignoreCase = true) &&
        (!filter.enabled || ((include == null || include.containsMatchIn(name)) &&
            (exclude == null || !exclude.containsMatchIn(name)) &&
            (filter.protocols.isEmpty() || type in filter.protocols)))
}

internal data class FlightFilterResult(val token: Long, val count: Long, val protocols: Set<String>)
