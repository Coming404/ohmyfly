package com.ohmyfly

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.utils.springAnimateToPage

@Composable
fun App(dynamicColorSeed: Color?, settingsPath: String, version: String) {
    PersistedSettings(settingsPath) { settings, onChange, session, onRetryRead ->
        AppMotion(settings.reduceMotion) {
            AppHaptics(settings.hapticEnabled, settings.hapticLevel) {
                AppContent(dynamicColorSeed, settings, onChange, version, session, onRetryRead)
            }
        }
    }
}

@Composable
private fun AppContent(
    dynamicColorSeed: Color?,
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
    val mirrorPage = destination == AppDestination.Settings && settingsPage == SettingsPage.Mirrors
    val themeMode = settings.themeMode
    val reduceTransparency = settings.reduceTransparency
    val systemReduceTransparency by rememberSystemReduceTransparency()
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
    val dark = themeMode.isDark(isSystemInDarkTheme())

    AppTheme(dynamicColorSeed, dark) {
        val background = LocalAppSurfaceColors.current.page
        val backdrop = rememberLayerBackdrop {
            drawRect(background)
            drawContent()
        }
        val glassEnabled = glassEffectsEnabled(reduceTransparency, glassShaderSupported(), systemReduceTransparency)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                DriftTopBar(
                    title = if (settingsChild) "" else destination.label,
                    backdrop = backdrop,
                    glassEnabled = glassEnabled,
                    dark = dark,
                    materialMode = settings.materialMode,
                    onBack = settingsBack,
                    actions = if (destination == AppDestination.NodeLibrary) {
                        {
                            NodeSelectionActions(
                                nodes = nodes,
                                category = category,
                                selectedIds = selectedIds,
                                onSelection = { selectedIds = it },
                            )
                        }
                    } else if (mirrorPage) {
                        { HeaderAction("添加镜像", MirrorIcons.Add, onClick = { mirrorAdd?.invoke() },
                            enabled = mirrorAdd != null && settings.mirrorEnabled && !settingsSession.state.readFailed) }
                    } else null,
                )
            },
            bottomBar = {
                if (!settingsChild) DriftNavigationBar(
                    destination = destination,
                    onDestination = { destination = it },
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
                Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = false,
                    ) { page ->
                        when (destinations[page]) {
                        AppDestination.Overview -> OverviewScreen(
                            listState = overviewState,
                            nodes = nodes,
                            subscriptionCount = 0,
                            selectedCount = nodes.count { it.id in selectedIds },
                            onNodes = { destination = AppDestination.NodeLibrary },
                            onSettings = { destination = AppDestination.Settings },
                            onSubscriptions = { destination = AppDestination.Subscriptions },
                            onSpeed = { destination = AppDestination.Speed },
                            contentPadding = padding,
                        )
                        AppDestination.Subscriptions -> SubscriptionsScreen(subscriptionsState, padding)
                        AppDestination.Speed -> SpeedScreen(
                            listState = speedState,
                            nodes = nodes,
                            selectedIds = selectedIds,
                            onNodes = { destination = AppDestination.NodeLibrary },
                            contentPadding = padding,
                        )
                        AppDestination.NodeLibrary -> NodesScreen(
                            listState = nodesState,
                            nodes = nodes,
                            category = category,
                            selectedIds = selectedIds,
                            onCategory = { category = it },
                            onSelection = { selectedIds = it },
                            contentPadding = padding,
                        )
                        AppDestination.Settings -> SettingsScreen(
                            listState = settingsState,
                            settings = settings,
                            onChange = onChange,
                            backStack = settingsStack,
                            onPage = { settingsStack.openSettingsPage(it) },
                            onBack = { settingsStack.popSettingsPage() },
                            editorBackActive = mirrorEditorBack != null,
                            onEditorBackChange = { mirrorEditorBack = it },
                            session = settingsSession,
                            runner = mirrorRunner,
                            onMirrorAddChange = { mirrorAdd = it },
                            version = version,
                            contentPadding = padding,
                        )
                        }
                    }
                }
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
}
