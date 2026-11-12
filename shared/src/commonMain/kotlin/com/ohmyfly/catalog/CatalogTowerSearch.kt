package com.ohmyfly.catalog

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

/** 原生塔台游标每批 100 条，搜索只保留一批匹配结果与计数，不累计全库 ID。 */
internal suspend fun CatalogStore.searchTowers(
    revision: Long,
    query: String,
    anchor: Long,
    backwards: Boolean,
    countAll: Boolean,
): CatalogPage {
    var cursor = anchor
    var count = 0L
    var metricsRevision = 0L
    val matches = mutableListOf<CatalogTower>()
    do {
        currentCoroutineContext().ensureActive()
        val page = catalogJson.decodeFromJsonElement<CatalogPage>(call("page") {
            put("kind", "towers"); put("revision", revision)
            put("anchor", cursor); put("backwards", backwards)
        })
        metricsRevision = page.metricsRevision
        val matching = page.towers.filter { it.name.contains(query.trim(), ignoreCase = true) }
        count += matching.size
        val remaining = 100 - matches.size
        if (backwards) matches.addAll(0, matching.takeLast(remaining))
        else matches.addAll(matching.take(remaining))
        if ((!countAll && matches.size == 100) || page.towers.size < 100) break
        cursor = if (backwards) page.towers.first().id else page.towers.last().id
    } while (true)
    return CatalogPage(revision = revision, metricsRevision = metricsRevision, matchCount = count, towers = matches)
}
