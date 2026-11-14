// 页面外壳统一持有设置子页动作，日志清理保留独立悬浮入口。
package com.ohmyfly

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ohmyfly.ui.AppDestination
import com.ohmyfly.ui.AppMessageHost
import com.ohmyfly.ui.AppMessageKind
import com.ohmyfly.ui.LocalAppMessages
import com.ohmyfly.ui.copyCatalogText
import com.ohmyfly.ui.exportCatalogYaml
import com.ohmyfly.ui.DriftNavigationBar
import com.ohmyfly.ui.DriftTopBar
import com.ohmyfly.ui.NodeSelectionActions
import com.ohmyfly.ui.NodeSummary
import com.ohmyfly.ui.NodesScreen
import com.ohmyfly.ui.OverviewScreen
import com.ohmyfly.ui.SettingsScreen
import com.ohmyfly.ui.SpeedScreen
import com.ohmyfly.ui.SubscriptionsScreen
import com.ohmyfly.ui.AppTheme
import com.ohmyfly.ui.ThemeMode
import com.ohmyfly.ui.AppHaptics
import com.ohmyfly.ui.hapticClick
import com.ohmyfly.ui.AppMotion
import com.ohmyfly.ui.CatalogSelectionMode
import com.ohmyfly.ui.CatalogSelectionToolbar
import com.ohmyfly.ui.CatalogSelectAllAction
import com.ohmyfly.ui.DriftIcons
import com.ohmyfly.ui.HeaderTextAction
import com.ohmyfly.ui.LocalAppMotionAllowed
import com.ohmyfly.ui.LocalAppSurfaceColors
import com.ohmyfly.ui.rememberSystemReduceTransparency
import com.ohmyfly.mirror.MirrorProbe
import com.ohmyfly.mirror.createMirrorTransport
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.ui.HeaderAction
import com.ohmyfly.ui.settings.MirrorDialog
import com.ohmyfly.ui.settings.MirrorIcons
import com.ohmyfly.ui.settings.MirrorProbeRunner
import com.ohmyfly.ui.settings.SettingsPage
import com.ohmyfly.ui.glass.glassEffectsEnabled
import com.ohmyfly.ui.glass.glassShaderSupported
import com.ohmyfly.ui.settings.openSettingsPage
import com.ohmyfly.ui.settings.popSettingsPage
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.utils.springAnimateToPage

private val subscriptionTowerStore get() = com.ohmyfly.catalog.appCatalog

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
    var towerSearchOpen by remember { mutableStateOf(false) }
    var flightSearchOpen by remember { mutableStateOf(false) }
    var towerImportOpen by remember { mutableStateOf(false) }
    val catalog by subscriptionTowerStore.state.collectAsState()
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
            mirrorPage, mirrorAdd, logsPage, logClear, nodes, selectedIds, onSelection = { selectedIds = it },
            selectedTowerIds = selectedTowerIds, selectionMode = selectionMode, selectionTowerId = selectionTowerId,
            onSelectionMode = { selectionMode = it }, onTowerSelection = { selectedTowerIds = it },
            onDestination = { destination = it },
            onCatalogSearch = {
                if (destination == AppDestination.Subscriptions) towerSearchOpen = !towerSearchOpen
                else flightSearchOpen = !flightSearchOpen
            },
            onTowerAdd = { towerImportOpen = true },
        ) { padding ->
            AppPager(
                pagerState, settings, onChange, version, settingsSession, mirrorRunner, settingsStack,
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
                onDestination = { destination = it }, editorBackActive = mirrorEditorBack != null,
                onEditorBackChange = { mirrorEditorBack = it }, onMirrorAddChange = { mirrorAdd = it },
                onLogClearChange = { logClear = it },
                overviewState = overviewState, subscriptionsState = subscriptionsState, speedState = speedState,
                towerSearchOpen = towerSearchOpen, onTowerSearch = { towerSearchOpen = it },
                flightSearchOpen = flightSearchOpen, onFlightSearch = { flightSearchOpen = it },
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
    nodes: List<NodeSummary>,
    selectedIds: Set<String>,
    onSelection: (Set<String>) -> Unit,
    selectedTowerIds: Set<Long>,
    selectionMode: CatalogSelectionMode?,
    selectionTowerId: Long,
    onSelectionMode: (CatalogSelectionMode?) -> Unit,
    onTowerSelection: (Set<Long>) -> Unit,
    onDestination: (AppDestination) -> Unit,
    onCatalogSearch: () -> Unit,
    onTowerAdd: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    var filterOpen by remember { mutableStateOf(false) }
    val messages = LocalAppMessages.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(destination) { if (destination != AppDestination.NodeLibrary) filterOpen = false }
    val background = LocalAppSurfaceColors.current.page
    val reduceTransparency = settings.reduceTransparency
    val systemReduceTransparency by rememberSystemReduceTransparency()
    val backdrop = rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
    val glassEnabled = glassEffectsEnabled(reduceTransparency, glassShaderSupported(), systemReduceTransparency)
    Scaffold(
        snackbarHost = { AppMessageHost(blocked = settingsSession.state.readFailed) },
        floatingActionButton = {
            if (destination == AppDestination.NodeLibrary && selectionMode == null) com.ohmyfly.ui.FlightFilterAction { filterOpen = true }
        },
        floatingToolbar = {
            selectionMode?.let { mode ->
                key(mode, selectionTowerId) {
                    com.ohmyfly.ui.CatalogSelectionHost(mode,
                        selectedIds.mapNotNull { it.toLongOrNull() }.toSet(), selectedTowerIds,
                        settingsSession.state.confirmedSettings ?: settings,
                        onDone = { onSelectionMode(null); onSelection(emptySet()); onTowerSelection(emptySet()) })
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(destination, settingsChild, settingsBack, mirrorPage, mirrorAdd, logsPage, logClear, nodes,
                selectedIds, onSelection, selectedTowerIds, selectionMode, selectionTowerId, onSelectionMode, onTowerSelection,
                backdrop, glassEnabled, dark, settings, settingsSession,
                onCatalogSearch = onCatalogSearch, onTowerAdd = onTowerAdd)
        },
        bottomBar = {
            if (!settingsChild && selectionMode == null) DriftNavigationBar(
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
                .then(if (glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier)
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
}

@Composable
private fun AppTopBar(
    destination: AppDestination,
    settingsChild: Boolean,
    settingsBack: (() -> Unit)?,
    mirrorPage: Boolean,
    mirrorAdd: (() -> Unit)?,
    logsPage: Boolean,
    logClear: (() -> Unit)?,
    nodes: List<NodeSummary>,
    selectedIds: Set<String>,
    onSelection: (Set<String>) -> Unit,
    selectedTowerIds: Set<Long>,
    selectionMode: CatalogSelectionMode?,
    selectionTowerId: Long,
    onSelectionMode: (CatalogSelectionMode?) -> Unit,
    onTowerSelection: (Set<Long>) -> Unit,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    dark: Boolean,
    settings: AppSettings,
    settingsSession: SettingsSession,
    onCatalogSearch: () -> Unit,
    onTowerAdd: () -> Unit,
) {
    DriftTopBar(
        title = if (selectionMode != null) "已选 ${if (selectionMode == CatalogSelectionMode.Nodes) selectedIds.size else selectedTowerIds.size}" else if (settingsChild || destination in listOf(AppDestination.Settings, AppDestination.Subscriptions, AppDestination.NodeLibrary)) "" else destination.label,
        backdrop = backdrop,
        glassEnabled = glassEnabled,
        dark = dark,
        materialMode = settings.materialMode,
        onBack = settingsBack,
        actions = when {
            selectionMode != null -> {
                {
                    HeaderTextAction("取消", DriftIcons.Clear, onClick = {
                        onSelectionMode(null); onSelection(emptySet()); onTowerSelection(emptySet())
                    })
                    key(selectionMode, selectionTowerId) {
                        CatalogSelectAllAction(selectionMode, selectionTowerId, onSelection, onTowerSelection)
                    }
                }
            }
            destination == AppDestination.NodeLibrary -> {
                {
                    HeaderAction("搜索航班", com.ohmyfly.ui.settings.SettingsIcons.Search, onClick = onCatalogSearch)
                }
            }
            destination == AppDestination.Subscriptions -> {
                {
                    HeaderAction("搜索塔台", com.ohmyfly.ui.settings.SettingsIcons.Search, onClick = onCatalogSearch)
                    HeaderAction("添加塔台", MirrorIcons.Add, onClick = onTowerAdd,
                        enabled = !settingsSession.state.readFailed)
                }
            }
            mirrorPage -> {
                { HeaderAction("添加镜像", MirrorIcons.Add, onClick = { mirrorAdd?.invoke() },
                    enabled = mirrorAdd != null && settings.mirrorEnabled && !settingsSession.state.readFailed) }
            }
            logsPage -> {
                { HeaderAction("清理全部日志", MirrorIcons.Trash, onClick = { logClear?.invoke() },
                    enabled = logClear != null && !settingsSession.state.readFailed,
                    tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.error) }
            }
            else -> null
        },
    )
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
    editorBackActive: Boolean,
    onEditorBackChange: ((() -> Unit)?) -> Unit,
    onMirrorAddChange: ((() -> Unit)?) -> Unit,
    onLogClearChange: ((() -> Unit)?) -> Unit,
    overviewState: LazyListState, subscriptionsState: LazyListState, speedState: LazyListState,
    towerSearchOpen: Boolean, onTowerSearch: (Boolean) -> Unit,
    flightSearchOpen: Boolean, onFlightSearch: (Boolean) -> Unit,
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
                searchOpen = towerSearchOpen, onSearchOpenChange = onTowerSearch,
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
                searchOpen = flightSearchOpen, onSearchOpenChange = onFlightSearch,
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
        else com.ohmyfly.ui.PageRefresh(padding, {
            if (AppDestination.entries[page] == AppDestination.Subscriptions) {
                val confirmed = checkNotNull(settingsSession.state.confirmedSettings) { "设置尚未读取完成" }
                check(!settingsSession.state.readFailed) { "设置读取失败，请先重试" }
                subscriptionTowerStore.syncAll(confirmed.subscription,
                    com.ohmyfly.subscription.SubscriptionMirrorSettings(confirmed.mirrorEnabled, confirmed.mirrors, confirmed.selectedMirrorId))
            } else subscriptionTowerStore.refreshView()
        }, pageContent)
    }
}
