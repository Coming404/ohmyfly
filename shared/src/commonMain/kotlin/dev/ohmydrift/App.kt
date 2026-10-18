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
import dev.ohmydrift.settings.AppSettings
import dev.ohmydrift.ui.settings.SettingsPage
import dev.ohmydrift.ui.settings.SettingsBackHandler
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun App(useSystemDynamicColors: Boolean, settingsPath: String, version: String) {
    PersistedSettings(settingsPath) { settings, onChange, writeError, onDismissError ->
        AppContent(useSystemDynamicColors, settings, onChange, version, writeError, onDismissError)
    }
}

@Composable
private fun AppContent(
    useSystemDynamicColors: Boolean,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    version: String,
    writeError: String?,
    onDismissError: () -> Unit,
) {
    var destination by remember { mutableStateOf(AppDestination.Overview) }
    var settingsPage by remember { mutableStateOf(SettingsPage.Home) }
    val themeMode = settings.themeMode
    val reduceTransparency = settings.reduceTransparency
    val settingsChild = destination == AppDestination.Settings && settingsPage != SettingsPage.Home
    val settingsBack: (() -> Unit)? = if (settingsChild) ({ settingsPage = SettingsPage.Home }) else null
    SettingsBackHandler(settingsChild) { settingsPage = SettingsPage.Home }
    var category by remember { mutableStateOf("全部") }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val nodes = emptyList<NodeSummary>()
    val overviewState = rememberLazyListState()
    val subscriptionsState = rememberLazyListState()
    val speedState = rememberLazyListState()
    val nodesState = rememberLazyListState()
    val settingsState = rememberLazyListState()
    val dark = themeMode.isDark(isSystemInDarkTheme())
    val themeController = remember(dark, useSystemDynamicColors) {
        ThemeController(
            colorSchemeMode = if (useSystemDynamicColors) ColorSchemeMode.MonetSystem else ColorSchemeMode.System,
            keyColor = null,
            isDark = dark,
        )
    }

    MiuixTheme(controller = themeController) {
        val background = MiuixTheme.colorScheme.surface
        val backdrop = rememberLayerBackdrop {
            drawRect(background)
            drawContent()
        }
        val glassEnabled = !reduceTransparency
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                DriftTopBar(destination, backdrop, glassEnabled, dark,
                    title = if (settingsChild) settingsPage.title else destination.label,
                    onBack = settingsBack,
                ) {
                    if (destination == AppDestination.NodeLibrary) {
                        NodeSelectionActions(
                            nodes = nodes,
                            category = category,
                            selectedIds = selectedIds,
                            onSelection = { selectedIds = it },
                        )
                    }
                }
            },
            bottomBar = {
                if (!settingsChild) DriftNavigationBar(
                    destination = destination,
                    onDestination = { destination = it },
                    backdrop = backdrop,
                    glassEnabled = glassEnabled,
                    dark = dark,
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
                            page = settingsPage,
                            onPage = { settingsPage = it },
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
