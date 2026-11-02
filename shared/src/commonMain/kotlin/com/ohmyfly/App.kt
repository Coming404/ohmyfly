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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import com.ohmyfly.ui.LocalAppMotionAllowed
import com.ohmyfly.ui.LocalAppSurfaceColors
import com.ohmyfly.ui.rememberSystemReduceTransparency
import com.ohmyfly.mirror.MirrorProbe
import com.ohmyfly.mirror.createMirrorTransport
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.nativecore.parseSubscriptionContent
import com.ohmyfly.subscription.SubscriptionFetcher
import com.ohmyfly.subscription.SubscriptionImporter
import com.ohmyfly.subscription.SubscriptionTowerStore
import com.ohmyfly.subscription.createSubscriptionClient
import com.ohmyfly.ui.HeaderAction
import com.ohmyfly.ui.settings.MirrorDialog
import com.ohmyfly.ui.settings.MirrorIcons
import com.ohmyfly.ui.settings.MirrorProbeRunner
import com.ohmyfly.ui.settings.SettingsPage
import com.ohmyfly.ui.glass.glassEffectsEnabled
import com.ohmyfly.ui.glass.glassShaderSupported
import com.ohmyfly.ui.settings.openSettingsPage
import com.ohmyfly.ui.settings.popSettingsPage
import kotlinx.coroutines.CoroutineScope
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.utils.springAnimateToPage

private val subscriptionTowerStore by lazy {
    SubscriptionTowerStore(
        SubscriptionImporter(SubscriptionFetcher(createSubscriptionClient()), ::parseSubscriptionContent),
    )
}

@Composable
fun App(
    dynamicColorSeed: Color?,
    settingsPath: String,
    version: String,
    onThemeChanged: (dark: Boolean, followsSystem: Boolean) -> Unit,
) {
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
    val subscriptionLoading = remember { mutableStateListOf<String>() }
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
    val mirrorPage = destination == AppDestination.Settings && settingsPage == SettingsPage.Mirrors
    val settingsChild = destination == AppDestination.Settings && settingsPage != SettingsPage.Home
    val settingsBack: (() -> Unit)? = if (settingsChild) {
        { mirrorEditorBack?.invoke() ?: settingsStack.popSettingsPage() }
    } else null
    var category by remember { mutableStateOf("全部") }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val nodes = emptyList<NodeSummary>()
    val overviewState = rememberLazyListState()
    val subscriptionsState = rememberLazyListState()
    val speedState = rememberLazyListState()
    val nodesState = rememberLazyListState()
    val settingsState = rememberLazyListState()

    AppTheme(dynamicColorSeed, dark) {
        AppFrame(
            dark, settings, settingsSession, onRetryRead, destination, settingsChild, settingsBack,
            mirrorPage, mirrorAdd, nodes, category, selectedIds, onSelection = { selectedIds = it },
            onDestination = { destination = it },
        ) { padding ->
            AppPager(
                pagerState, settings, onChange, version, settingsSession, mirrorRunner, settingsStack,
                nodes, category, selectedIds, onCategory = { category = it }, onSelection = { selectedIds = it },
                onDestination = { destination = it }, editorBackActive = mirrorEditorBack != null,
                onEditorBackChange = { mirrorEditorBack = it }, onMirrorAddChange = { mirrorAdd = it },
                overviewState = overviewState, subscriptionsState = subscriptionsState, speedState = speedState,
                nodesState = nodesState, settingsState = settingsState, scope = navScope,
                subscriptionLoading = subscriptionLoading, padding = padding,
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
    nodes: List<NodeSummary>,
    category: String,
    selectedIds: Set<String>,
    onSelection: (Set<String>) -> Unit,
    onDestination: (AppDestination) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val background = LocalAppSurfaceColors.current.page
    val reduceTransparency = settings.reduceTransparency
    val systemReduceTransparency by rememberSystemReduceTransparency()
    val backdrop = rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
    val glassEnabled = glassEffectsEnabled(reduceTransparency, glassShaderSupported(), systemReduceTransparency)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(destination, settingsChild, settingsBack, mirrorPage, mirrorAdd, nodes, category,
                selectedIds, onSelection, backdrop, glassEnabled, dark, settings, settingsSession)
        },
        bottomBar = {
            if (!settingsChild) DriftNavigationBar(
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
        AppMessageHost(padding, backdrop, glassEnabled, dark, settings.materialMode, blocked = settingsSession.state.readFailed)
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
    nodes: List<NodeSummary>,
    category: String,
    selectedIds: Set<String>,
    onSelection: (Set<String>) -> Unit,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    dark: Boolean,
    settings: AppSettings,
    settingsSession: SettingsSession,
) {
    DriftTopBar(
        title = if (settingsChild) "" else destination.label,
        backdrop = backdrop,
        glassEnabled = glassEnabled,
        dark = dark,
        materialMode = settings.materialMode,
        onBack = settingsBack,
        actions = if (destination == AppDestination.NodeLibrary) {
            { NodeSelectionActions(nodes = nodes, category = category, selectedIds = selectedIds, onSelection = onSelection) }
        } else if (mirrorPage) {
            { HeaderAction("添加镜像", MirrorIcons.Add, onClick = { mirrorAdd?.invoke() },
                enabled = mirrorAdd != null && settings.mirrorEnabled && !settingsSession.state.readFailed) }
        } else null,
    )
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
    category: String,
    selectedIds: Set<String>,
    onCategory: (String) -> Unit,
    onSelection: (Set<String>) -> Unit,
    onDestination: (AppDestination) -> Unit,
    editorBackActive: Boolean,
    onEditorBackChange: ((() -> Unit)?) -> Unit,
    onMirrorAddChange: ((() -> Unit)?) -> Unit,
    overviewState: LazyListState, subscriptionsState: LazyListState, speedState: LazyListState,
    nodesState: LazyListState, settingsState: LazyListState,
    scope: CoroutineScope,
    subscriptionLoading: SnapshotStateList<String>,
    padding: PaddingValues,
) {
    val towers by subscriptionTowerStore.towers.collectAsState()
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) { page ->
        when (AppDestination.entries[page]) {
            AppDestination.Overview -> OverviewScreen(
                listState = overviewState, nodes = nodes, subscriptionCount = towers.size,
                selectedCount = nodes.count { it.id in selectedIds },
                onNodes = { onDestination(AppDestination.NodeLibrary) },
                onSettings = { onDestination(AppDestination.Settings) },
                onSubscriptions = { onDestination(AppDestination.Subscriptions) },
                onSpeed = { onDestination(AppDestination.Speed) },
                contentPadding = padding,
            )
            AppDestination.Subscriptions -> SubscriptionsScreen(
                listState = subscriptionsState, store = subscriptionTowerStore, settings = settings.subscription,
                scope = scope, loading = subscriptionLoading, contentPadding = padding,
            )
            AppDestination.Speed -> SpeedScreen(
                listState = speedState, nodes = nodes, selectedIds = selectedIds,
                onNodes = { onDestination(AppDestination.NodeLibrary) },
                contentPadding = padding,
            )
            AppDestination.NodeLibrary -> NodesScreen(
                listState = nodesState, nodes = nodes, category = category, selectedIds = selectedIds,
                onCategory = onCategory, onSelection = onSelection, contentPadding = padding,
            )
            AppDestination.Settings -> SettingsScreen(
                listState = settingsState,
                settings = settings,
                onChange = onChange, backStack = settingsStack,
                onPage = { settingsStack.openSettingsPage(it) },
                onBack = { settingsStack.popSettingsPage() },
                editorBackActive = editorBackActive, onEditorBackChange = onEditorBackChange,
                session = settingsSession, runner = mirrorRunner,
                onMirrorAddChange = onMirrorAddChange,
                version = version, contentPadding = padding,
            )
        }
    }
}
