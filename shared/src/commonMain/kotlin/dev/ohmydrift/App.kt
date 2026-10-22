package dev.ohmydrift

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.ohmydrift.ui.AppDestination
import dev.ohmydrift.ui.DriftNavigationBar
import dev.ohmydrift.ui.DriftTopBar
import dev.ohmydrift.ui.NodeSelectionActions
import dev.ohmydrift.ui.NodeSummary
import dev.ohmydrift.ui.NodesScreen
import dev.ohmydrift.ui.OverviewScreen
import dev.ohmydrift.ui.SettingsScreen
import dev.ohmydrift.ui.SpeedScreen
import dev.ohmydrift.ui.SubscriptionsScreen
import dev.ohmydrift.ui.AppTheme
import dev.ohmydrift.ui.AppMotion
import dev.ohmydrift.ui.LocalAppSurfaceColors
import dev.ohmydrift.ui.rememberSystemReduceTransparency
import androidx.compose.ui.draw.blur
import dev.ohmydrift.settings.SettingsSession
import dev.ohmydrift.ui.HeaderAction
import dev.ohmydrift.ui.settings.MirrorIcons
import dev.ohmydrift.settings.AppSettings
import dev.ohmydrift.ui.glass.glassEffectsEnabled
import dev.ohmydrift.ui.glass.glassShaderSupported
import dev.ohmydrift.ui.settings.SettingsPage
import dev.ohmydrift.ui.settings.openSettingsPage
import dev.ohmydrift.ui.settings.popSettingsPage
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack

@Composable
fun App(dynamicColorSeed: Color?, settingsPath: String, version: String) {
    PersistedSettings(settingsPath) { settings, onChange, writeError, onDismissError, session ->
        AppMotion(settings.reduceMotion) {
            AppContent(dynamicColorSeed, settings, onChange, version, writeError, onDismissError, session)
        }
    }
}

@Composable
private fun AppContent(
    dynamicColorSeed: Color?,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    version: String,
    writeError: String?,
    onDismissError: () -> Unit,
    settingsSession: SettingsSession,
) {
    var destination by rememberSaveable(
        stateSaver = Saver<AppDestination, String>(
            save = { it.name },
            restore = { name -> AppDestination.entries.firstOrNull { it.name == name } ?: AppDestination.Overview },
        ),
    ) { mutableStateOf(AppDestination.Overview) }
    val settingsStack = rememberNavBackStack<SettingsPage>(SettingsPage.Home)
    val settingsPage = settingsStack.last() as SettingsPage
    var mirrorEditorBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    var mirrorAdd by remember { mutableStateOf<(() -> Unit)?>(null) }
    var mirrorOverlay by remember { mutableStateOf(false) }
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
        val depth = if (mirrorPage && mirrorOverlay && glassEnabled) 2.dp else 0.dp
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(Modifier.blur(depth)) {
                DriftTopBar(
                    title = if (mirrorPage) "" else if (settingsChild) settingsPage.title else destination.label,
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
                }
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
                    .blur(depth)
                    .background(background),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                    when (destination) {
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
                            onMirrorAddChange = { mirrorAdd = it },
                            onMirrorOverlayChange = { mirrorOverlay = it },
                            version = version,
                            writeError = writeError,
                            onDismissError = onDismissError,
                            contentPadding = padding,
                        )
                    }
                }
            }
        }
    }
}
