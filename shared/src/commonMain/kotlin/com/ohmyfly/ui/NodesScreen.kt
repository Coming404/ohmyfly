package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
    searchState: CatalogSearchState,
    searchActive: Boolean,
    selectionMode: CatalogSelectionMode? = null,
    onEnterSelection: () -> Unit = {},
    onSubscriptions: () -> Unit = {},
) {
    FlightLibraryList(listState, catalog, selectedIds, onSelection, selectionMode, onEnterSelection,
        onPage, onSubscriptions, contentPadding, searchState, searchActive)
}

// 搜索和分组随列表滚动；安全区作为内容留白，使卡片可从浮动导航栏下方经过。
@Composable
private fun FlightLibraryList(listState: LazyListState, catalog: CatalogState, selectedIds: Set<String>,
    onSelection: (Set<String>) -> Unit, selectionMode: CatalogSelectionMode?, onEnterSelection: () -> Unit,
    onPage: (Int) -> Unit, onSubscriptions: () -> Unit, contentPadding: PaddingValues,
    searchState: CatalogSearchState, searchActive: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val inSelection = selectionMode == CatalogSelectionMode.Nodes
    LaunchedEffect(selectionMode) { if (inSelection) expanded = emptyList() }
    LaunchedEffect(catalog.appliedFilter) { listState.scrollToItem(0) }
    val count = catalog.matchCount ?: 0
    val hasStatus = catalog.failure != null || !catalog.ready || catalog.matchCount == null || catalog.measurementsFailure != null
    val searchVisibility = rememberSearchVisibility(searchState.expanded)
    val searchOpen = searchVisibility.currentState || searchVisibility.targetState
    val headerCount = 1 + (if (searchOpen) 1 else 0) + (if (hasStatus) 1 else 0)
    LaunchedEffect(searchState.focusRequested, searchActive) {
        if (searchState.focusRequested && searchActive) listState.scrollToItem(0)
    }
    CatalogViewport(listState, headerCount, catalog.flightPage, catalog.flightWindow.keys, count,
        catalog.failure != null, onPage)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState,
        contentPadding = contentPadding.withPageSpacing(LocalLayoutDirection.current, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (searchOpen) item("search") {
            androidx.compose.animation.AnimatedVisibility(visibleState = searchVisibility) {
                CatalogSearch(catalog.flightFilter.search, "搜索航班", searchState, searchActive) {
                    appCatalog.updateFlightFilter(catalog.flightFilter.copy(search = it), debounce = true)
                }
            }
        }
        item("groups") { FlightGroups(catalog) }
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
                !catalog.ready || catalog.matchCount == null -> if (catalog.summary.flights > 0L) {
                    CatalogLoadingIndicator("正在加载航班", Modifier.fillParentMaxSize())
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(7) { CatalogLoading("正在筛选…") }
                    }
                }
                else -> Unit
            }
            catalog.measurementsFailure?.let { CatalogNotice(it) }
        }
        items(count.coerceAtMost(Int.MAX_VALUE.toLong() - 1).toInt(), key = { "flight-slot:$it" }) { index ->
            val flight = catalog.flightWindow[index / 100]?.getOrNull(index % 100)
            if (flight == null) {
                if (catalog.summary.flights > 0L && index % 100 == 0) {
                    CatalogLoadingIndicator("正在加载航班", Modifier.fillParentMaxSize())
                } else if (catalog.summary.flights == 0L) CatalogLoading()
                else Spacer(Modifier.height(1.dp))
            } else FlightCard(flight, flight.id.toString() in selectedIds,
                onSelect = { if (!inSelection) onEnterSelection(); onSelection(toggleSelection(selectedIds, flight.id.toString())) },
                onDetails = { expanded = if (flight.id in expanded) expanded - flight.id else expanded + flight.id },
                expanded = flight.id in expanded, selectionMode = inSelection)
        }
        }
        if (catalog.failure == null && catalog.ready && catalog.matchCount != null && count == 0L) {
            val noFlights = catalog.summary.flights == 0L
            val emptyGroup = !noFlights && catalog.appliedFilter.let {
                it.groupId >= 0 && it.search.isBlank() && !it.active
            }
            Box(
                Modifier.fillMaxSize().padding(
                    contentPadding.withPageSpacing(LocalLayoutDirection.current, horizontal = 12.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                CatalogEmpty(
                    title = if (noFlights) "还没有航班" else if (emptyGroup) "此分组暂无航班" else "没有匹配的航班",
                    hint = if (noFlights) "添加并同步塔台后，航班会显示在这里。"
                        else if (emptyGroup) "切换其他分组，或将航班移动到这里。" else "调整分组、搜索或筛选条件，查看其他航班。",
                    onAction = if (emptyGroup) null else if (noFlights) onSubscriptions else ({
                        appCatalog.updateFlightFilter(FlightFilter())
                        searchState.close()
                    }),
                    actionLabel = if (noFlights) "前往塔台" else "清除筛选",
                    icon = if (noFlights) DriftIcons.Nodes else SettingsIcons.Search,
                )
            }
        }
    }
}
