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
    // 标题由 Scaffold 固定；分组紧接顶栏，关闭的搜索区不占据列表位置。
    Column(Modifier.fillMaxSize().padding(contentPadding.withPageSpacing(LocalLayoutDirection.current, horizontal = 12.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (searchOpen) CatalogSearch(catalog.flightFilter.search, "搜索航班", "航班", catalog.matchCount ?: 0,
            expandedState = true, onExpandedChange = onSearchOpenChange, onQuery = {
                appCatalog.updateFlightFilter(catalog.flightFilter.copy(search = it), debounce = true)
            })
        FlightGroups(catalog)
        FlightLibraryList(listState, catalog, selectedIds, onSelection, selectionMode, onEnterSelection,
            onPage, onSubscriptions, onSearchOpenChange, Modifier.weight(1f))
    }
}

// 列表独占分组下方的可用空间，空状态在该空间内居中。
@Composable
private fun FlightLibraryList(listState: LazyListState, catalog: CatalogState, selectedIds: Set<String>,
    onSelection: (Set<String>) -> Unit, selectionMode: CatalogSelectionMode?, onEnterSelection: () -> Unit,
    onPage: (Int) -> Unit, onSubscriptions: () -> Unit, onSearchOpenChange: (Boolean) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val inSelection = selectionMode == CatalogSelectionMode.Nodes
    LaunchedEffect(selectionMode) { if (inSelection) expanded = emptySet() }
    LaunchedEffect(catalog.filterRevision) { listState.scrollToItem(0) }
    val count = catalog.matchCount ?: 0
    val hasStatus = catalog.failure != null || !catalog.ready || catalog.matchCount == null || count == 0L || catalog.measurementsFailure != null
    CatalogViewport(listState, if (hasStatus) 1 else 0, catalog.flightPage, catalog.flightWindow.keys, count,
        catalog.failure != null, onPage)
    LazyColumn(modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasStatus) item("status") {
            when {
                catalog.failure != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    val emptyGroup = !noFlights && catalog.appliedFilter.let { it.groupId >= 0 && it.search.isBlank() && !it.active }
                    CatalogEmpty(
                        title = if (noFlights) "还没有航班" else if (emptyGroup) "此分组暂无航班" else "没有匹配的航班",
                        hint = if (noFlights) "添加并同步塔台后，航班会显示在这里。"
                            else if (emptyGroup) "切换其他分组，或将航班移动到这里。" else "调整分组、搜索或筛选条件，查看其他航班。",
                        onAction = if (emptyGroup) null else if (noFlights) onSubscriptions else ({
                            appCatalog.updateFlightFilter(FlightFilter()); onSearchOpenChange(false)
                        }),
                        actionLabel = if (noFlights) "前往塔台" else "清除筛选",
                        icon = if (noFlights) DriftIcons.Nodes else SettingsIcons.Search,
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }
            }
            catalog.measurementsFailure?.let { CatalogNotice(it) }
        }
        items(count.coerceAtMost(Int.MAX_VALUE.toLong() - 1).toInt(), key = { "flight-slot:$it" }) { index ->
            val flight = catalog.flightWindow[index / 100]?.getOrNull(index % 100)
            if (flight == null) CatalogLoading()
            else FlightCard(flight, flight.id.toString() in selectedIds,
                onSelect = { if (!inSelection) onEnterSelection(); onSelection(toggleSelection(selectedIds, flight.id.toString())) },
                onDetails = { expanded = if (flight.id in expanded) expanded - flight.id else expanded + flight.id },
                expanded = flight.id in expanded, revision = catalog.summary.revision, selectionMode = inSelection)
        }
    }
}
