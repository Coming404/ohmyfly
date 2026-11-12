package com.ohmyfly.catalog

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

// 远跳只保留当前扫描批次；取消或修订变化立即丢弃，未建立全库锚点表。
internal suspend fun CatalogStore.loadWindowPage(
    kind: String, revision: Long, filterToken: Long, search: String, countAll: Boolean,
    start: Int, index: Int, anchor: Long, backwards: Boolean,
): CatalogPage {
    var cursor = anchor
    var pageIndex = start
    while (true) {
        currentCoroutineContext().ensureActive()
        val page = if (kind == "towers") searchTowers(revision, search, cursor, backwards, countAll)
        else catalogJson.decodeFromJsonElement<CatalogPage>(call("page") {
            put("kind", kind); put("anchor", cursor); put("backwards", backwards)
            put("revision", revision); put("filterToken", filterToken)
        })
        if (pageIndex == index) return page
        val ids = if (kind == "towers") page.towers.map { it.id } else page.flights.map { it.id }
        check(ids.isNotEmpty()) { "目录批次已失效，请重新读取" }
        cursor = if (backwards) ids.first() else ids.last()
        pageIndex += if (backwards) -1 else 1
    }
}
