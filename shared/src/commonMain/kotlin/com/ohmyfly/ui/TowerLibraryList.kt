// 总控与塔台卡片共用一个滚动列表；删除待撤销时隐藏卡片，不插入描述性占位。
package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.*
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.subscription.SubscriptionMirrorSettings

/** 搜索、总控和卡片统一消费列表内边距，只有 Scaffold 顶栏固定。 */
@Composable
internal fun TowerLibraryList(listState: LazyListState, catalog: CatalogState, store: CatalogStore,
    settings: AppSettings, importReady: Boolean, selectedTowerIds: Set<Long>, onTowerSelection: (Set<Long>) -> Unit,
    selectionMode: CatalogSelectionMode?, selectionTowerId: Long, onEnterSelection: () -> Unit,
    selectedNodeIds: Set<String>, onNodeSelection: (Set<String>) -> Unit, onEnterNodeSelection: (Long) -> Unit,
    contentPadding: PaddingValues, searchState: CatalogSearchState, searchActive: Boolean, onSync: () -> Unit,
    onAdd: () -> Unit, onEdit: (CatalogTower) -> Unit, onSourceImport: (CatalogSource) -> Unit,
    onReimport: (CatalogTower) -> Unit, modifier: Modifier,
) {
    var expanded by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val messages = LocalAppMessages.current
    val mirrors = SubscriptionMirrorSettings(settings.mirrorEnabled, settings.mirrors, settings.selectedMirrorId)
    val count = catalog.towerMatchCount ?: 0
    val hasStatus = catalog.failure != null || catalog.towerMatchCount == null
    val searchOpen = searchState.expanded
    val showControl = catalog.summary.towers > 0
    val headerCount = (if (showControl) 1 else 0) + (if (searchOpen) 1 else 0) + (if (hasStatus) 1 else 0)
    // 用户打开搜索时滚回输入框；恢复页面或滚动重建搜索项不再请求焦点。
    LaunchedEffect(searchState.focusRequested, searchActive) {
        if (searchState.focusRequested && searchActive) listState.scrollToItem(0)
    }
    CatalogViewport(listState, headerCount, catalog.towerPage, catalog.towerWindow.keys, count,
        catalog.failure != null) { store.page("towers", it) }
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState,
        contentPadding = contentPadding.withPageSpacing(LocalLayoutDirection.current, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (searchOpen) item("search") {
            CatalogSearch(catalog.towerSearch, "按塔台名称搜索",
                state = searchState, active = searchActive, onQuery = store::updateTowerSearch)
        }
        if (showControl) item("control") {
            TowerControl(catalog, importReady, onCancel = store::cancelOperation, onSync = onSync)
        }
        if (hasStatus) item("status") {
            TowerListStatus(catalog, count, store, Modifier.fillParentMaxSize(), onAdd)
        }
        items(count.coerceAtMost(Int.MAX_VALUE.toLong() - 1).toInt(), key = { "tower-slot:$it" }) { index ->
            val tower = catalog.towerWindow[index / 100]?.getOrNull(index % 100)
            if (tower == null) {
                if (catalog.summary.towers > 0L) CatalogLoadingIndicator("正在加载塔台")
                else CatalogTowerLoading()
            } else if (tower.id !in catalog.pendingRemoval) TowerCard(tower,
                catalog.sourceTasks.values.any { it.parentId == tower.id }, importReady && catalog.ready,
                phase = catalog.sourceTasks.values.firstOrNull { it.parentId == tower.id }?.phase,
                selected = tower.id in selectedTowerIds, selectionMode = selectionMode == CatalogSelectionMode.Towers,
                onSelect = { if (selectionMode != CatalogSelectionMode.Towers) onEnterSelection()
                    onTowerSelection(toggleLongSelection(selectedTowerIds, tower.id)) },
                selectedNodeIds = if (selectionTowerId == tower.id) selectedNodeIds else emptySet(),
                nodeSelectionMode = selectionMode == CatalogSelectionMode.Nodes,
                onNodeSelection = { onEnterNodeSelection(tower.id); onNodeSelection(it) },
                onSourceImport = onSourceImport, onEdit = { onEdit(tower) },
                expanded = tower.id in expanded, revision = catalog.summary.revision,
                onExpand = { expanded = if (tower.id in expanded) expanded - tower.id else expanded + tower.id },
                onRefresh = {
                    if (tower.sourceKind == "http" || tower.sourceKind == "merged") {
                        try { store.refresh(tower.id, settings.subscription, mirrors) }
                        catch (e: Exception) { messages.show(AppMessageKind.Failure, e.message ?: "同步未加入队列") }
                    } else onReimport(tower)
                },
                onRemove = { postponeTowerRemoval(tower, store, messages) })
        }
        }
        if (catalog.failure == null && catalog.towerMatchCount != null && count == 0L) {
            val searching = catalog.towerSearch.isNotBlank()
            Box(
                Modifier.fillMaxSize().padding(
                    contentPadding.withPageSpacing(LocalLayoutDirection.current, horizontal = 12.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                CatalogEmpty(
                    title = if (searching) "没有匹配的塔台" else "还没有塔台",
                    hint = if (searching) "换个名称搜索，或清除条件查看全部塔台。" else "添加订阅地址、协议链接或 YAML 文件，开始接收航班。",
                    onAction = if (searching) ({ store.updateTowerSearch("") }) else onAdd,
                    actionLabel = if (searching) "清除搜索" else "添加塔台",
                    icon = if (searching) SettingsIcons.Search else DriftIcons.Nodes,
                )
            }
        }
    }
}

/** 撤销窗口结束后才执行真实删除；点击撤销只恢复卡片。 */
private fun postponeTowerRemoval(tower: CatalogTower, store: CatalogStore, messages: AppMessageState) {
    store.postponeRemoval(tower.id)
    messages.show(AppMessage(AppMessageKind.Uncertain, "待删除 ${tower.name}", "撤销",
        onAction = {
            store.cancelRemoval(tower.id)
            messages.show(AppMessageKind.Success, "已撤销删除")
        }, onDismiss = {
            store.remove(tower.id) { success, text ->
                messages.show(if (success) AppMessageKind.Success else AppMessageKind.Failure, text)
            }
        }))
}
