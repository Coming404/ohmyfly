// 塔台列表独占总控下方视口；删除待撤销时隐藏卡片，不插入描述性占位。
package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.*
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.subscription.SubscriptionMirrorSettings

/** 卡片展开和列表滚动独立于固定的顶部总控。 */
@Composable
internal fun TowerLibraryList(listState: LazyListState, catalog: CatalogState, store: CatalogStore,
    settings: AppSettings, importReady: Boolean, selectedTowerIds: Set<Long>, onTowerSelection: (Set<Long>) -> Unit,
    selectionMode: CatalogSelectionMode?, selectionTowerId: Long, onEnterSelection: () -> Unit,
    selectedNodeIds: Set<String>, onNodeSelection: (Set<String>) -> Unit, onEnterNodeSelection: (Long) -> Unit,
    onAdd: () -> Unit, onEdit: (CatalogTower) -> Unit, onSourceImport: (CatalogSource) -> Unit,
    onReimport: (CatalogTower) -> Unit, modifier: Modifier,
) {
    var expanded by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val messages = LocalAppMessages.current
    val mirrors = SubscriptionMirrorSettings(settings.mirrorEnabled, settings.mirrors, settings.selectedMirrorId)
    val count = catalog.towerMatchCount ?: 0
    val hasStatus = catalog.failure != null || catalog.towerMatchCount == null || count == 0L
    CatalogViewport(listState, if (hasStatus) 1 else 0, catalog.towerPage, catalog.towerWindow.keys, count,
        catalog.failure != null) { store.page("towers", it) }
    LazyColumn(modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hasStatus) item("status") {
            TowerListStatus(catalog, count, store, Modifier.fillParentMaxSize(), onAdd)
        }
        items(count.coerceAtMost(Int.MAX_VALUE.toLong() - 1).toInt(), key = { "tower-slot:$it" }) { index ->
            val tower = catalog.towerWindow[index / 100]?.getOrNull(index % 100)
            if (tower == null) CatalogLoading()
            else if (tower.id !in catalog.pendingRemoval) TowerCard(tower,
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
