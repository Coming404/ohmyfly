// 全选只跨桥接读取 ID，结果绑定当前筛选与目录版本。
package com.ohmyfly.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal suspend fun CatalogStore.selectionIds(kind: String, towerId: Long = 0): Set<Long> {
    val snapshot = state.value
    return withContext(Dispatchers.Default) {
        val ids = linkedSetOf<Long>()
        var anchor = 0L
        do {
            currentCoroutineContext().ensureActive()
            val page = call("selectionPage") {
                put("kind", kind); put("id", towerId); put("anchor", anchor)
                put("revision", snapshot.summary.revision); put("filterToken", snapshot.filterToken)
                put("name", snapshot.towerSearch.trim())
            }.getValue("ids").jsonArray.map { it.jsonPrimitive.long }
            ids.addAll(page)
            check(ids.size <= 100_000) { "一次最多选择 100000 个航班或塔台，请缩小筛选范围" }
            if (page.size < 1000) break
            anchor = page.last()
        } while (true)
        val current = state.value
        check(current.summary.revision == snapshot.summary.revision &&
            current.filterToken == snapshot.filterToken && current.towerSearch == snapshot.towerSearch) {
            "塔台或航班列表已更新，请重新全选"
        }
        ids
    }
}
