package com.ohmyfly.catalog

import kotlinx.serialization.Serializable

@Serializable
internal data class DeduplicationRule(
    val mode: String = "none",
    val commonFields: List<String> = emptyList(),
    val protocolRules: Map<String, List<String>> = emptyMap(),
)
