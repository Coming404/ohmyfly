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
internal fun TowerFlights(id: Long, revision: Long, selectedNodeIds: Set<String> = emptySet(),
    onNodeSelection: (Set<String>) -> Unit = {}, selectionMode: Boolean = false) {
    key(id, revision) { TowerFlightWindow(id, revision, selectedNodeIds, onNodeSelection, selectionMode) }
}

@Composable
private fun TowerFlightWindow(id: Long, revision: Long, selectedNodeIds: Set<String>,
    onNodeSelection: (Set<String>) -> Unit, selectionMode: Boolean) {
    val list = rememberLazyListState()
    var pages by remember { mutableStateOf<Map<Int, CatalogPage>>(emptyMap()) }
    var target by remember { mutableStateOf(0) }
    var count by remember { mutableStateOf<Long?>(null) }
    var failure by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(target) {
        if (target in pages) return@LaunchedEffect
        failure = false
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
                val window = pages.toMutableMap()
                window[batch] = loaded
                while (window.size > 5) window.remove(window.keys.maxBy { kotlin.math.abs(it - target) })
                pages = window
                if (loaded.flights.size < 100) break
                anchor = loaded.flights.last().id
                batch++
            }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { failure = true }
    }
    CatalogViewport(list, 0, target, pages.keys, count ?: 0, failure) { target = it }
    when {
        failure -> CatalogNotice("来源航班读取失败，请重新展开")
        count == null -> CatalogLoading()
        count == 0L -> CatalogEmpty("暂无航班", "")
        else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), state = list) {
            items(count!!.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), key = { "tower-flight:$it" }) { index ->
                val flight = pages[index / 100]?.flights?.getOrNull(index % 100)
                if (flight == null) CatalogLoading()
                else {
                    FlightRow(flight, flight.id.toString() in selectedNodeIds, selectionMode,
                        onSelect = { onNodeSelection(toggleSelection(selectedNodeIds, flight.id.toString())) },
                        onDetails = { expanded = if (expanded == flight.id) null else flight.id },
                        expanded = expanded == flight.id)
                    CatalogExpansion(expanded == flight.id && !selectionMode) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider()
                            FlightDetails(flight, revision)
                        }
                    }
                }
            }
        }
    }
}
