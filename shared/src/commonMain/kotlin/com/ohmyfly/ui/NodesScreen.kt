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
    onSubscriptions: () -> Unit = {},
) {
    // 原型允许多个航班详情同时展开；进入选择模式时统一收起详情。
    var expanded by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val inSelection = selectionMode == CatalogSelectionMode.Nodes
    LaunchedEffect(selectionMode) {
        if (selectionMode == CatalogSelectionMode.Nodes) expanded = emptySet()
    }
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
                !catalog.ready || catalog.matchCount == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(7) { CatalogLoading("正在筛选…") }
                }
                count == 0L -> {
                    val noFlights = catalog.summary.flights == 0L
                    CatalogEmpty(
                        title = if (noFlights) "还没有航班" else "没有匹配的航班",
                        onAction = if (noFlights) onSubscriptions else {
                            {
                                appCatalog.updateFlightFilter(FlightFilter())
                                onSearchOpenChange(false)
                            }
                        },
                        actionLabel = if (noFlights) "前往塔台" else "清除筛选",
                        icon = if (noFlights) DriftIcons.Nodes else SettingsIcons.Search,
                    )
                }
            }
            catalog.measurementsFailure?.let { CatalogNotice(it) }
        }
        items(count.coerceAtMost(Int.MAX_VALUE.toLong() - 2).toInt(), key = { "flight-slot:$it" }) { index ->
            val flight = catalog.flightWindow[index / 100]?.getOrNull(index % 100)
            if (flight == null) CatalogLoading()
            else FlightCard(flight, flight.id.toString() in selectedIds,
                onSelect = { if (!inSelection) onEnterSelection(); onSelection(toggleSelection(selectedIds, flight.id.toString())) },
                onDetails = { expanded = if (flight.id in expanded) expanded - flight.id else expanded + flight.id },
                expanded = flight.id in expanded, revision = catalog.summary.revision, selectionMode = inSelection)
        }
    }
}
