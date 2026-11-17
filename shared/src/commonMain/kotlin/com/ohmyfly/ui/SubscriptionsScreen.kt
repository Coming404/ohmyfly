package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
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
    selectedTowerIds: Set<Long> = emptySet(),
    onTowerSelection: (Set<Long>) -> Unit = {},
    selectionMode: CatalogSelectionMode? = null,
    selectionTowerId: Long = 0L,
    onEnterSelection: () -> Unit = {},
    selectedNodeIds: Set<String> = emptySet(),
    onNodeSelection: (Set<String>) -> Unit = {},
    onEnterNodeSelection: (Long) -> Unit = {},
    searchOpen: Boolean = false,
    onSearchOpenChange: (Boolean) -> Unit = {},
    importOpen: Boolean = false,
    onImportOpenChange: (Boolean) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    val catalog by store.state.collectAsState()
    val mirrors = SubscriptionMirrorSettings(settings.mirrorEnabled, settings.mirrors, settings.selectedMirrorId)
    val messages = LocalAppMessages.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(importOpen) }
    LaunchedEffect(importOpen) { importing = importOpen }
    LaunchedEffect(importing) { onImportOpenChange(importing) }
    var target by remember { mutableStateOf<CatalogTower?>(null) }
    var editing by remember { mutableStateOf<CatalogTower?>(null) }
    LaunchedEffect(catalog.towerSearch) { listState.scrollToItem(0) }
    LaunchedEffect(catalog.failure) { catalog.failure?.let { messages.show(AppMessageKind.Failure, it) } }
    Column(Modifier.fillMaxSize().padding(contentPadding.withPageSpacing(LocalLayoutDirection.current, horizontal = 12.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (searchOpen) CatalogSearch(catalog.towerSearch, "按塔台名称搜索", "塔台", catalog.summary.towers,
            expandedState = true, onExpandedChange = onSearchOpenChange, onQuery = store::updateTowerSearch)
        if (catalog.summary.towers > 0 || catalog.busy) TowerControl(catalog, importReady,
            onCancel = store::cancelOperation, onSync = {
                scope.launch {
                    try { store.syncAll(settings.subscription, mirrors) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { messages.show(AppMessageKind.Failure, "同步未全部完成，请查看塔台状态") }
                }
            })
        TowerLibraryList(listState, catalog, store, settings, importReady, selectedTowerIds, onTowerSelection,
            selectionMode, selectionTowerId, onEnterSelection, selectedNodeIds, onNodeSelection, onEnterNodeSelection,
            onAdd = { target = null; importing = true }, onEdit = { editing = it },
            onSourceImport = { source ->
                target = CatalogTower(source.id, source.name, 0, source.failure, source.kind, address = source.address)
                importing = true
            }, onReimport = { target = it; importing = true }, modifier = Modifier.weight(1f))
    }
    if (importing) TowerImportPanel(importReady && catalog.ready, settings.subscription, target,
        onSubmit = { inputs, name, merge, id -> store.importSources(inputs, name, merge, settings.subscription, mirrors, id) },
        onClose = { importing = false; target = null })
    editing?.let { tower -> TowerEditSheet(tower, store) { editing = null } }
}

@Composable
internal fun TowerListStatus(catalog: CatalogState, count: Long, store: CatalogStore, modifier: Modifier, onAdd: () -> Unit) {
    when {
        catalog.failure != null -> Column {
            CatalogNotice(catalog.failure)
            SettingsButton("重试", SettingsIcons.Retry) { if (catalog.ready) store.page("towers", catalog.towerPage) else store.start() }
        }
        catalog.towerMatchCount == null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { CatalogTowerLoading() }
        }
        count == 0L -> {
            val searching = catalog.towerSearch.isNotBlank()
            CatalogEmpty(
                title = if (searching) "没有匹配的塔台" else "还没有塔台",
                hint = if (searching) "换个名称搜索，或清除条件查看全部塔台。" else "添加订阅地址、协议链接或 YAML 文件，开始接收航班。",
                modifier = modifier,
                onAction = if (searching) ({ store.updateTowerSearch("") }) else onAdd,
                actionLabel = if (searching) "清除搜索" else "添加塔台",
                icon = if (searching) SettingsIcons.Search else DriftIcons.Nodes,
            )
        }
    }
}
