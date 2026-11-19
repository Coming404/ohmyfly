// 页面外壳统一持有设置子页动作，日志清理保留独立悬浮入口。
package com.ohmyfly

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.ohmyfly.ui.*
import com.ohmyfly.catalog.selectionIds
import com.ohmyfly.mirror.MirrorProbe
import com.ohmyfly.mirror.createMirrorTransport
import kotlinx.coroutines.*
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.ui.settings.*
import com.ohmyfly.ui.glass.glassEffectsEnabled
import com.ohmyfly.ui.glass.glassShaderSupported
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.*
import com.ohmyfly.ui.glass.rememberChromeRecording
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.utils.springAnimateToPage

private val subscriptionTowerStore get() = com.ohmyfly.catalog.appCatalog

// Compose 入口由 Android 模块调用；Swift 仅通过 MainViewController 进入共享界面。
@OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
@kotlin.native.HiddenFromObjC
@Composable
fun App(
    dynamicColorSeed: Color?,
    settingsPath: String,
    version: String,
    filePicker: com.ohmyfly.subscription.SubscriptionFilePicker,
    onThemeChanged: (dark: Boolean, followsSystem: Boolean) -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(com.ohmyfly.subscription.LocalSubscriptionFilePicker provides filePicker) {
    PersistedSettings(settingsPath) { settings, onChange, session, onRetryRead ->
        val themeMode = settings.themeMode
        val dark = themeMode.isDark(isSystemInDarkTheme())
        LaunchedEffect(dark, themeMode) {
            onThemeChanged(dark, themeMode == ThemeMode.System)
        }
        AppMotion(settings.reduceMotion) {
            AppHaptics(settings.hapticEnabled, settings.hapticLevel) {
                AppContent(dynamicColorSeed, dark, settings, onChange, version, session, onRetryRead)
            }
        }
    }
    }
}

@Composable
private fun AppContent(
    dynamicColorSeed: Color?,
    dark: Boolean,
    settings: AppSettings,
    onChange: (String, (AppSettings) -> AppSettings) -> Unit,
    version: String,
    settingsSession: SettingsSession,
    onRetryRead: () -> Unit,
) {
    var destination by rememberSaveable(
        stateSaver = Saver<AppDestination, String>(
            save = { it.name },
            restore = { name -> AppDestination.entries.firstOrNull { it.name == name } ?: AppDestination.Overview },
        ),
    ) { mutableStateOf(AppDestination.Overview) }
    val navScope = rememberCoroutineScope()
    val motionAllowed = LocalAppMotionAllowed.current
    val mirrorRunner = remember(settingsSession, navScope) {
        MirrorProbeRunner(MirrorProbe(createMirrorTransport()), navScope)
    }
    DisposableEffect(mirrorRunner) {
        onDispose { mirrorRunner.stop() }
    }
    val destinations = AppDestination.entries
    val pagerState = rememberPagerState(
        initialPage = destinations.indexOf(destination).coerceAtLeast(0),
        pageCount = { destinations.size },
    )
    LaunchedEffect(destination, motionAllowed) {
        val targetPage = destinations.indexOf(destination)
        if (pagerState.settledPage != targetPage) {
            if (motionAllowed) pagerState.springAnimateToPage(targetPage)
            else pagerState.scrollToPage(targetPage)
        }
    }
    val settingsStack = rememberNavBackStack<SettingsPage>(SettingsPage.Home)
    val settingsPage = settingsStack.last() as SettingsPage
    var mirrorEditorBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    var mirrorAdd by remember { mutableStateOf<(() -> Unit)?>(null) }
    var logClear by remember { mutableStateOf<(() -> Unit)?>(null) }
    val logsPage = destination == AppDestination.Settings && settingsPage == SettingsPage.Logs
    val mirrorPage = destination == AppDestination.Settings && settingsPage == SettingsPage.Mirrors
    val settingsChild = destination == AppDestination.Settings && settingsPage != SettingsPage.Home
    val settingsBack: (() -> Unit)? = if (settingsChild) {
        { mirrorEditorBack?.invoke() ?: settingsStack.popSettingsPage() }
    } else null
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedTowerIds by remember { mutableStateOf(emptySet<Long>()) }
    var selectionTowerId by remember { mutableStateOf(0L) }
    var selectionMode by remember { mutableStateOf<CatalogSelectionMode?>(null) }
    val towerSearch = remember { CatalogSearchState() }
    val flightSearch = remember { CatalogSearchState() }
    var towerImportOpen by remember { mutableStateOf(false) }
    val catalog by subscriptionTowerStore.state.collectAsState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // 导航开始前取消聚焦请求，避免分页动画中的离屏页面重新唤起键盘。
    val navigate: (AppDestination) -> Unit = { next ->
        if (next != destination) {
            val current = subscriptionTowerStore.state.value
            towerSearch.leave(current.towerSearch)
            flightSearch.leave(current.flightFilter.search)
            focus.clearFocus(force = true)
            keyboard?.hide()
            destination = next
        }
    }
    val toggleSearch: () -> Unit = {
        val search = if (destination == AppDestination.Subscriptions) towerSearch else flightSearch
        if (search.expanded) {
            focus.clearFocus(force = true)
            keyboard?.hide()
            search.close()
            if (destination == AppDestination.Subscriptions) subscriptionTowerStore.updateTowerSearch("")
            else subscriptionTowerStore.updateFlightFilter(subscriptionTowerStore.state.value.flightFilter.copy(search = ""))
        } else search.open()
    }
    val appMessages = LocalAppMessages.current
    var selectingAll by remember { mutableStateOf(false) }
    val selectedNodeIds = selectedIds.mapNotNullTo(linkedSetOf()) { it.toLongOrNull() }
    val selectionCount = if (selectionMode == CatalogSelectionMode.Nodes) selectedNodeIds.size else selectedTowerIds.size
    val selectionTotal = when (selectionMode) {
        CatalogSelectionMode.Towers -> catalog.towerMatchCount
        CatalogSelectionMode.Nodes -> if (selectionTowerId == 0L) catalog.matchCount
            else catalog.towerWindow.values.flatten().firstOrNull { it.id == selectionTowerId }?.nodeCount
        null -> null
    }
    val selectionAllLabel = if (selectionTotal != null && selectionCount.toLong() == selectionTotal) "全不选" else "全选"
    val cancelSelection = {
        selectionMode = null; selectedIds = emptySet(); selectedTowerIds = emptySet(); selectionTowerId = 0L
    }
    val selectAll = {
        val mode = selectionMode
        if (mode != null && !selectingAll) {
            val towerId = selectionTowerId
            selectingAll = true
            navScope.launch {
                try {
                    val ids = subscriptionTowerStore.selectionIds(
                        if (mode == CatalogSelectionMode.Nodes) "flights" else "towers",
                        if (mode == CatalogSelectionMode.Nodes) towerId else 0L,
                    )
                    if (selectionMode == mode && selectionTowerId == towerId) {
                        if (mode == CatalogSelectionMode.Nodes) {
                            val current = selectedIds.mapNotNullTo(linkedSetOf()) { it.toLongOrNull() }
                            selectedIds = if (ids == current) emptySet() else ids.mapTo(linkedSetOf()) { it.toString() }
                        } else selectedTowerIds = if (ids == selectedTowerIds) emptySet() else ids
                    }
                } catch (e: CancellationException) { throw e
                } catch (_: Exception) {
                    appMessages.show(AppMessageKind.Failure, "无法读取完整列表，请刷新后重试")
                } finally { selectingAll = false }
            }
        }
    }
    LaunchedEffect(Unit) { subscriptionTowerStore.start() }
    ApplyOutputDeduplication(catalog.ready, settingsSession)
    val nodes = remember(catalog.flightWindow) { catalog.flightWindow.entries.sortedBy { it.key }.flatMap { it.value }.map {
        NodeSummary(id = it.id.toString(), name = it.name, region = "", protocol = it.type)
    } }
    LaunchedEffect(catalog.filterRevision) {
        selectedIds = emptySet()
        if (selectionMode == CatalogSelectionMode.Nodes) selectionMode = null
    }
    LaunchedEffect(catalog.towerSearch) {
        selectedIds = emptySet(); selectedTowerIds = emptySet(); selectionMode = null
    }
    com.ohmyfly.catalog.CatalogDestination(destination, catalog.summary.revision) {
        selectedIds = emptySet(); selectedTowerIds = emptySet(); selectionMode = null
    }
    val overviewState = rememberLazyListState()
    val subscriptionsState = rememberLazyListState()
    val speedState = rememberLazyListState()
    val nodesState = rememberLazyListState()
    val settingsState = rememberLazyListState()

    AppTheme(dynamicColorSeed, dark) {
        AppFrame(
            dark, settings, settingsSession, onRetryRead, destination, settingsChild, settingsBack,
            mirrorPage, mirrorAdd, logsPage, logClear, selectedIds, onSelection = { selectedIds = it },
            selectedTowerIds = selectedTowerIds, selectionMode = selectionMode, selectionTowerId = selectionTowerId,
            selectionAllLabel = selectionAllLabel, selectingAll = selectingAll,
            onCancelSelection = cancelSelection, onSelectAll = selectAll,
            onSelectionMode = { selectionMode = it }, onTowerSelection = { selectedTowerIds = it },
            onDestination = navigate,
            onCatalogSearch = toggleSearch,
            onTowerAdd = { towerImportOpen = true },
            towerCount = catalog.summary.towers,
        ) { padding ->
            AppPager(
                pagerState, destination, settings, onChange, version, settingsSession, mirrorRunner, settingsStack,
                nodes, selectedIds, onSelection = { selectedIds = it },
                selectionMode = selectionMode, selectionTowerId = selectionTowerId, selectedTowerIds = selectedTowerIds,
                onTowerSelection = { selectedTowerIds = it },
                onEnterSelection = {
                    selectedTowerIds = emptySet(); selectionTowerId = 0L; selectionMode = CatalogSelectionMode.Nodes
                },
                onEnterNodeSelection = {
                    selectedTowerIds = emptySet(); selectionTowerId = it; selectionMode = CatalogSelectionMode.Nodes
                },
                onEnterTowerSelection = {
                    selectedIds = emptySet(); selectionTowerId = 0L; selectionMode = CatalogSelectionMode.Towers
                },
                onDestination = navigate, onSubscriptions = { navigate(AppDestination.Subscriptions) }, editorBackActive = mirrorEditorBack != null,
                onEditorBackChange = { mirrorEditorBack = it }, onMirrorAddChange = { mirrorAdd = it },
                onLogClearChange = { logClear = it },
                overviewState = overviewState, subscriptionsState = subscriptionsState, speedState = speedState,
                towerSearch = towerSearch, flightSearch = flightSearch,
                towerImportOpen = towerImportOpen, onTowerImport = { towerImportOpen = it },
                nodesState = nodesState, settingsState = settingsState, padding = padding,
            )
        }
    }
}

@Composable
private fun AppFrame(
    dark: Boolean,
    settings: AppSettings,
    settingsSession: SettingsSession,
    onRetryRead: () -> Unit,
    destination: AppDestination,
    settingsChild: Boolean,
    settingsBack: (() -> Unit)?,
    mirrorPage: Boolean,
    mirrorAdd: (() -> Unit)?,
    logsPage: Boolean,
    logClear: (() -> Unit)?,
    selectedIds: Set<String>,
    onSelection: (Set<String>) -> Unit,
    selectedTowerIds: Set<Long>,
    selectionMode: CatalogSelectionMode?,
    selectionTowerId: Long,
    selectionAllLabel: String,
    selectingAll: Boolean,
    onCancelSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectionMode: (CatalogSelectionMode?) -> Unit,
    onTowerSelection: (Set<Long>) -> Unit,
    onDestination: (AppDestination) -> Unit,
    onCatalogSearch: () -> Unit,
    onTowerAdd: () -> Unit,
    towerCount: Long,
    content: @Composable (PaddingValues) -> Unit,
) {
    var filterOpen by remember { mutableStateOf(false) }
    val messages = LocalAppMessages.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val scope = rememberCoroutineScope()
    LaunchedEffect(destination) { if (destination != AppDestination.NodeLibrary) filterOpen = false }
    val background = LocalAppSurfaceColors.current.page
    val reduceTransparency = settings.reduceTransparency
    val systemReduceTransparency by rememberSystemReduceTransparency()
    val recording = rememberChromeRecording(settings.materialMode) {
        drawRect(background)
        drawContent()
    }
    val backdrop = recording.backdrop
    val glassEnabled = glassEffectsEnabled(reduceTransparency, glassShaderSupported(), systemReduceTransparency)
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        floatingToolbar = {
            selectionMode?.let { mode ->
                key(mode, selectionTowerId) {
                    com.ohmyfly.ui.CatalogSelectionHost(mode,
                        selectedIds.mapNotNull { it.toLongOrNull() }.toSet(), selectedTowerIds,
                        settingsSession.state.confirmedSettings ?: settings,
                        toolbarModifier = Modifier.chromeMaterial(backdrop, glassEnabled, dark, settings.materialMode),
                        onDone = { onSelectionMode(null); onSelection(emptySet()); onTowerSelection(emptySet()) })
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(destination, settingsChild, settingsBack, mirrorPage, mirrorAdd, logsPage, logClear,
                selectedIds, selectedTowerIds, selectionMode,
                selectionAllLabel, selectingAll, onCancelSelection, onSelectAll,
                backdrop, glassEnabled, dark, settings, settingsSession,
                onCatalogSearch = onCatalogSearch, onTowerAdd = onTowerAdd,
                onFlightFilter = { filterOpen = true }, towerCount = towerCount)
        },
        bottomBar = {
            if (!settingsChild && selectionMode == null && !imeVisible) DriftNavigationBar(
                destination = destination,
                onDestination = onDestination,
                backdrop = backdrop,
                glassEnabled = glassEnabled,
                dark = dark,
                materialMode = settings.materialMode,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding)
                .then(if (glassEnabled) recording.record else Modifier)
                .background(background),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) { content(padding) }
        }
        com.ohmyfly.ui.FlightFilterSheet(filterOpen, onClose = { filterOpen = false })
        MirrorDialog(
            show = settingsSession.state.readFailed,
            title = if (settingsSession.state.writeUncertain) "设置结果待确认" else "读取设置失败",
            icon = MirrorIcons.Alert,
            dismissible = false,
            onDismiss = {},
            onDismissFinished = {},
            footer = { TextButton(text = "重试", onClick = hapticClick(onRetryRead)) },
        ) { Text(if (settingsSession.state.writeUncertain) "已提交，但未能读回" else "未更改本地数据") }
    }
    AppMessageHost(blocked = settingsSession.state.readFailed,
        modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun ApplyOutputDeduplication(ready: Boolean, session: SettingsSession) {
    val rule = session.state.confirmedSettings?.subscription?.outputDeduplication
    LaunchedEffect(ready, rule, session.state.readFailed) {
        if (ready && rule != null && !session.state.readFailed) {
            subscriptionTowerStore.updateOutputDeduplication(rule)
        }
    }
}

@Composable
private fun AppPager(
    pagerState: PagerState,
    destination: AppDestination,
    settings: AppSettings,
    onChange: (String, (AppSettings) -> AppSettings) -> Unit,
    version: String,
    settingsSession: SettingsSession,
    mirrorRunner: MirrorProbeRunner,
    settingsStack: NavBackStack,
    nodes: List<NodeSummary>,
    selectedIds: Set<String>,
    onSelection: (Set<String>) -> Unit,
    selectionMode: CatalogSelectionMode?,
    selectionTowerId: Long,
    selectedTowerIds: Set<Long>,
    onTowerSelection: (Set<Long>) -> Unit,
    onEnterSelection: () -> Unit,
    onEnterTowerSelection: () -> Unit,
    onEnterNodeSelection: (Long) -> Unit,
    onDestination: (AppDestination) -> Unit,
    onSubscriptions: () -> Unit,
    editorBackActive: Boolean,
    onEditorBackChange: ((() -> Unit)?) -> Unit,
    onMirrorAddChange: ((() -> Unit)?) -> Unit,
    onLogClearChange: ((() -> Unit)?) -> Unit,
    overviewState: LazyListState, subscriptionsState: LazyListState, speedState: LazyListState,
    towerSearch: CatalogSearchState, flightSearch: CatalogSearchState,
    towerImportOpen: Boolean, onTowerImport: (Boolean) -> Unit,
    nodesState: LazyListState, settingsState: LazyListState,
    padding: PaddingValues,
) {
    val catalog by subscriptionTowerStore.state.collectAsState()
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) { page ->
        val searchActive = AppDestination.entries[page] == destination &&
            pagerState.settledPage == page && !pagerState.isScrollInProgress
        val pageContent: @Composable () -> Unit = {
        when (AppDestination.entries[page]) {
            AppDestination.Overview -> OverviewScreen(
                listState = overviewState, nodes = nodes, subscriptionCount = catalog.summary.towers.toInt(),
                flightCount = catalog.summary.flights, catalogFailure = catalog.failure,
                selectedCount = nodes.count { it.id in selectedIds },
                onNodes = { onDestination(AppDestination.NodeLibrary) },
                onSettings = { onDestination(AppDestination.Settings) },
                onSubscriptions = { onDestination(AppDestination.Subscriptions) },
                onSpeed = { onDestination(AppDestination.Speed) },
                contentPadding = padding,
            )
            AppDestination.Subscriptions -> SubscriptionsScreen(
                listState = subscriptionsState, store = subscriptionTowerStore,
                settings = settingsSession.state.confirmedSettings ?: settings,
                importReady = settingsSession.state.confirmedSettings != null && !settingsSession.state.readFailed,
                selectedTowerIds = selectedTowerIds, selectionMode = selectionMode,
                selectionTowerId = selectionTowerId, onEnterSelection = onEnterTowerSelection,
                onTowerSelection = onTowerSelection, selectedNodeIds = selectedIds,
                onNodeSelection = onSelection, onEnterNodeSelection = onEnterNodeSelection,
                searchState = towerSearch, searchActive = searchActive,
                importOpen = towerImportOpen, onImportOpenChange = onTowerImport,
                contentPadding = padding,
            )
            AppDestination.Speed -> SpeedScreen(
                listState = speedState, nodes = nodes, selectedIds = selectedIds,
                onNodes = { onDestination(AppDestination.NodeLibrary) },
                contentPadding = padding,
            )
            AppDestination.NodeLibrary -> NodesScreen(
                listState = nodesState, nodes = nodes, selectedIds = selectedIds,
                onSelection = onSelection, contentPadding = padding,
                catalog = catalog, onPage = { subscriptionTowerStore.page("flights", it) },
                selectionMode = selectionMode, onEnterSelection = onEnterSelection,
                searchState = flightSearch, searchActive = searchActive,
                onSubscriptions = onSubscriptions,
            )
            AppDestination.Settings -> SettingsScreen(
                listState = settingsState,
                settings = settings,
                onChange = onChange, backStack = settingsStack,
                onPage = { settingsStack.openSettingsPage(it) },
                onBack = { settingsStack.popSettingsPage() },
                editorBackActive = editorBackActive, onEditorBackChange = onEditorBackChange,
                session = settingsSession, runner = mirrorRunner,
                onMirrorAddChange = onMirrorAddChange, onLogClearChange = onLogClearChange,
                version = version, contentPadding = padding,
            )
        }
        }
        if (AppDestination.entries[page] in listOf(AppDestination.Speed, AppDestination.Settings)) pageContent()
        else com.ohmyfly.ui.PageRefresh(padding,
            onRefresh = { subscriptionTowerStore.refreshView(invalidate = true) },
            refreshingText = when (AppDestination.entries[page]) {
                AppDestination.Subscriptions -> "正在刷新塔台"
                AppDestination.NodeLibrary -> "正在重新获取航班"
                else -> "正在刷新概览"
            }, content = pageContent)
    }
}
