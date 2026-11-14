package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.*
import com.ohmyfly.ui.settings.SettingsButton
import com.ohmyfly.ui.settings.SettingsIcons

@Composable
internal fun NodesScreen(
    listState: LazyListState,
    nodes: List<NodeSummary>,
    selectedIds: Set<String>,
    onSelection: (Set<String>) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    catalog: CatalogState,
    onPage: (Int) -> Unit,
    searchOpen: Boolean = false,
    onSearchOpenChange: (Boolean) -> Unit = {},
    selectionMode: CatalogSelectionMode? = null,
    onEnterSelection: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf<Long?>(null) }
    val inSelection = selectionMode == CatalogSelectionMode.Nodes
    val count = catalog.matchCount ?: 0
    CatalogViewport(listState, 2, catalog.flightPage, catalog.flightWindow.keys, count,
        catalog.failure != null, onPage)
    LaunchedEffect(catalog.filterRevision) { listState.scrollToItem(0) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), state = listState,
        contentPadding = contentPadding.withPageSpacing(LocalLayoutDirection.current, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("search") {
            CatalogSearch(catalog.flightFilter.search, "搜索航班", "航班", count,
                expandedState = searchOpen, onExpandedChange = onSearchOpenChange, onQuery = {
                appCatalog.updateFlightFilter(catalog.flightFilter.copy(search = it), debounce = true)
            })
        }
        item("status") {
            FlightGroups(catalog)
            when {
                catalog.failure != null -> Column {
                    CatalogNotice(catalog.failure)
                    SettingsButton("重试", SettingsIcons.Retry) {
                        if (!catalog.ready) appCatalog.start()
                        else if (catalog.filterToken == 0L) appCatalog.updateFlightFilter(catalog.appliedFilter)
                        else onPage(catalog.flightPage)
                    }
                }
                !catalog.ready || catalog.matchCount == null -> CatalogLoading("正在筛选…")
                count == 0L -> CatalogEmpty("暂无匹配航班", "添加塔台或调整搜索与筛选条件")
            }
            catalog.measurementsFailure?.let { CatalogNotice(it) }
        }
        items(count.coerceAtMost(Int.MAX_VALUE.toLong() - 2).toInt(), key = { "flight-slot:$it" }) { index ->
            val flight = catalog.flightWindow[index / 100]?.getOrNull(index % 100)
            if (flight == null) CatalogLoading()
            else FlightCard(flight, flight.id.toString() in selectedIds,
                onSelect = { if (!inSelection) onEnterSelection(); onSelection(toggleSelection(selectedIds, flight.id.toString())) },
                onDetails = { expanded = if (expanded == flight.id) null else flight.id },
                expanded = expanded == flight.id, revision = catalog.summary.revision, selectionMode = inSelection)
        }
    }
}
