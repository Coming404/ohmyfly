// 塔台子列表按游标连续加载；只保留五批摘要，配置随展开按需读取。
package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import top.yukonga.miuix.kmp.basic.HorizontalDivider

@Composable
internal fun TowerFlights(id: Long, revision: Long, expectedCount: Long, selectedNodeIds: Set<String> = emptySet(),
    onNodeSelection: (Set<String>) -> Unit = {}, selectionMode: Boolean = false) {
    key(id, revision) { TowerFlightWindow(id, revision, expectedCount, selectedNodeIds, onNodeSelection, selectionMode) }
}

@Composable
private fun TowerFlightWindow(id: Long, revision: Long, expectedCount: Long, selectedNodeIds: Set<String>,
    onNodeSelection: (Set<String>) -> Unit, selectionMode: Boolean) {
    val list = rememberLazyListState()
    var pages by remember { mutableStateOf<Map<Int, CatalogPage>>(emptyMap()) }
    var target by remember { mutableStateOf(0) }
    var count by remember { mutableStateOf<Long?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    // 内嵌航班详情沿用原型的多项展开行为，不互相关闭。
    var expanded by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(target, retry) {
        if (target in pages) return@LaunchedEffect
        failure = null
        mismatch = false
        try {
            val cached = pages.entries.filter { it.key < target }.maxByOrNull { it.key }
            var batch = cached?.key?.plus(1) ?: 0
            var anchor = cached?.value?.flights?.lastOrNull()?.id ?: 0
            while (batch <= target) {
                val loaded = withContext(Dispatchers.Default) {
                    catalogJson.decodeFromJsonElement<CatalogPage>(catalogCall("towerFlights") {
                        put("id", id); put("revision", revision); put("anchor", anchor)
                    })
                }
                count = loaded.matchCount
                mismatch = loaded.matchCount != expectedCount
                val window = pages.toMutableMap()
                window[batch] = loaded
                while (window.size > 5) window.remove(window.keys.maxBy { kotlin.math.abs(it - target) })
                pages = window
                if (loaded.flights.size < 100) break
                anchor = loaded.flights.last().id
                batch++
            }
        } catch (e: CancellationException) { throw e
        } catch (error: Exception) { failure = logCatalogFailure("读取塔台 $id 航班（修订 $revision，批次 $target）", error) }
    }
    CatalogViewport(list, 0, target, pages.keys, count ?: 0L, failure != null) { target = it }
    val reload = {
        count = null
        mismatch = false
        pages = emptyMap()
        target = 0
        failure = null
        retry = retry + 1
    }
    val hasFlights = pages.values.any { it.flights.isNotEmpty() }
    val incompletePage = count != null && pages.any { (page, value) ->
        value.flights.size < 100 && page * 100L + value.flights.size < count!!
    }
    when {
        failure != null && !hasFlights -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CatalogNotice("航班读取失败：$failure")
            CatalogTextButton("重试", onClick = reload)
        }
        count == null -> CatalogLoading()
        incompletePage || count == 0L && expectedCount > 0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CatalogNotice("塔台航班数量与列表不一致，请重新读取", CatalogNoticeKind.Warning)
            CatalogTextButton("重新读取", onClick = reload)
        }
        count == 0L -> CatalogEmpty("暂无航班", "")
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (failure != null) {
                CatalogNotice("后续航班读取失败，已保留当前列表：$failure")
                CatalogTextButton("重试", onClick = reload)
            } else if (mismatch) {
                CatalogNotice("塔台数据刚刚更新，已显示最新航班列表", CatalogNoticeKind.Warning)
            }
            TowerFlightList(
                count = count!!, pages = pages, list = list, revision = revision,
                selectedNodeIds = selectedNodeIds, selectionMode = selectionMode,
                expanded = expanded, onExpanded = { id ->
                    expanded = if (id in expanded) expanded - id else expanded + id
                },
                onNodeSelection = onNodeSelection,
            )
        }
    }
}

// 航班行与详情保持在塔台卡片内展开，分页窗口缺页时显示读取中的占位。
@Composable
private fun TowerFlightList(
    count: Long,
    pages: Map<Int, CatalogPage>,
    list: androidx.compose.foundation.lazy.LazyListState,
    revision: Long,
    selectedNodeIds: Set<String>,
    selectionMode: Boolean,
    expanded: Set<Long>,
    onExpanded: (Long) -> Unit,
    onNodeSelection: (Set<String>) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), state = list,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), key = { "tower-flight:$it" }) { index ->
            val flight = pages[index / 100]?.flights?.getOrNull(index % 100)
            if (flight == null) CatalogLoading()
            else {
                FlightRow(flight, flight.id.toString() in selectedNodeIds, selectionMode,
                    onSelect = { onNodeSelection(toggleSelection(selectedNodeIds, flight.id.toString())) },
                    onDetails = { onExpanded(flight.id) },
                    expanded = flight.id in expanded)
                CatalogExpansion(flight.id in expanded && !selectionMode) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider()
                        FlightDetails(flight)
                    }
                }
            }
        }
    }
}
