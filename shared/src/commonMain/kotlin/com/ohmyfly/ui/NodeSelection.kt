package com.ohmyfly.ui

internal fun nodeCategories(nodes: List<NodeSummary>): List<String> =
    listOf("全部") + nodes.map { it.region }.distinct().filter { it != "全部" }

internal fun filterNodes(nodes: List<NodeSummary>, category: String): List<NodeSummary> =
    nodes.filter { category == "全部" || it.region == category }

internal fun toggleSelection(selected: Set<String>, id: String): Set<String> =
    if (id in selected) selected - id else selected + id

/** A bulk toggle changes visible items only; hidden selections are preserved. */
internal fun toggleVisibleSelection(selected: Set<String>, visibleIds: Set<String>): Set<String> =
    if (selected.containsAll(visibleIds)) selected - visibleIds else selected + visibleIds
