package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.subscription.SubscriptionMirrorSettings
import com.ohmyfly.catalog.*
import com.ohmyfly.ui.settings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SubscriptionsScreen(
    listState: LazyListState,
    store: CatalogStore,
    settings: AppSettings,
    importReady: Boolean,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val catalog by store.state.collectAsState()
    val mirrors = SubscriptionMirrorSettings(settings.mirrorEnabled, settings.mirrors, settings.selectedMirrorId)
    val messages = LocalAppMessages.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<CatalogTower?>(null) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    val count = catalog.towerMatchCount ?: 0
    LaunchedEffect(catalog.towerSearch) { listState.scrollToItem(0) }
    LaunchedEffect(catalog.failure) { catalog.failure?.let { messages.show(AppMessageKind.Failure, it) } }
    CatalogViewport(listState, 3, catalog.towerPage, catalog.towerWindow.keys, count,
        catalog.failure != null) { store.page("towers", it) }
    LazyColumn(Modifier.fillMaxSize().imePadding(), state = listState,
        contentPadding = contentPadding.withPageSpacing(LocalLayoutDirection.current, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("search") {
            CatalogSearch(catalog.towerSearch, "按塔台名称搜索", "塔台", catalog.summary.towers,
                onAdd = { target = null; importing = true }, addEnabled = importReady && catalog.ready,
                onQuery = store::updateTowerSearch)
        }
        item("control") {
            if (catalog.summary.towers > 0 || catalog.busy) TowerControl(catalog, importReady,
                onCancel = store::cancelOperation, onSync = {
                    scope.launch {
                        try { store.syncAll(settings.subscription, mirrors) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { messages.show(AppMessageKind.Failure, "同步未全部完成，请查看塔台状态") }
                    }
                })
        }
        item("status") { TowerListStatus(catalog, count, store) { target = null; importing = true } }
        items(count.coerceAtMost(Int.MAX_VALUE.toLong() - 3).toInt(), key = { "tower-slot:$it" }) { index ->
            val tower = catalog.towerWindow[index / 100]?.getOrNull(index % 100)
            if (tower == null) CatalogLoading()
            else if (tower.id in catalog.pendingRemoval) SettingsHint("等待删除确认，可在底部撤销")
            else TowerCard(tower, catalog.sourceTasks.values.any { it.parentId == tower.id }, importReady && catalog.ready,
                phase = catalog.sourceTasks.values.firstOrNull { it.parentId == tower.id }?.phase,
                onSourceImport = { source ->
                    target = CatalogTower(source.id, source.name, 0, source.failure, source.kind)
                    importing = true
                },
                expanded = expanded == tower.id, revision = catalog.summary.revision,
                onExpand = { expanded = if (expanded == tower.id) null else tower.id },
                onRefresh = {
                    if (tower.sourceKind == "http" || tower.sourceKind == "merged") {
                        try { store.refresh(tower.id, settings.subscription, mirrors) }
                        catch (e: Exception) { messages.show(AppMessageKind.Failure, e.message ?: "同步未加入队列") }
                    } else { target = tower; importing = true }
                },
                onRemove = {
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
                })
        }
    }
    if (importing) TowerImportPanel(importReady && catalog.ready, settings.subscription, target,
        onSubmit = { inputs, name, merge, id -> store.importSources(inputs, name, merge, settings.subscription, mirrors, id) },
        onClose = { importing = false; target = null })
}

@Composable
private fun TowerListStatus(catalog: CatalogState, count: Long, store: CatalogStore, onAdd: () -> Unit) {
    when {
        catalog.failure != null -> Column {
            SettingsHint(catalog.failure, isError = true)
            SettingsButton("重试", SettingsIcons.Retry) { if (catalog.ready) store.page("towers", catalog.towerPage) else store.start() }
        }
        catalog.towerMatchCount == null -> CatalogLoading()
        count == 0L -> CatalogEmpty(if (catalog.towerSearch.isBlank()) "还没有塔台" else "没有匹配的塔台",
            if (catalog.towerSearch.isBlank()) "添加订阅链接、配置文件或节点正文" else "尝试更换关键词",
            if (catalog.towerSearch.isBlank()) onAdd else null)
    }
}
