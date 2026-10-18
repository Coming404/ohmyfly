package dev.ohmydrift

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import dev.ohmydrift.ui.AppDestination
import dev.ohmydrift.ui.DriftNavigationBar
import dev.ohmydrift.ui.DriftTopBar
import dev.ohmydrift.ui.HeaderAction
import dev.ohmydrift.ui.NodeSelectionActions
import dev.ohmydrift.ui.SettingsScreen
import dev.ohmydrift.ui.SpeedTestScreen
import dev.ohmydrift.ui.SubscriptionsScreen
import dev.ohmydrift.ui.DriftIcons
import dev.ohmydrift.ui.NodesScreen
import dev.ohmydrift.ui.OverviewScreen
import dev.ohmydrift.ui.ThemeMode
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
fun App() {
    var destination by remember { mutableStateOf(AppDestination.Overview) }
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    var reduceMotion by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("全部") }
    var selectedIds by remember { mutableStateOf(setOf("tokyo")) }
    val overviewState = rememberLazyListState()
    val subscriptionsState = rememberLazyListState()
    val speedTestState = rememberLazyListState()
    val nodesState = rememberLazyListState()
    val settingsState = rememberLazyListState()
    val dark = themeMode.isDark(isSystemInDarkTheme())
    val themeController = remember(dark) {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.MonetSystem,
            // An explicit seed makes Vibrant / Spec2025 effective on both platforms.
            keyColor = Color(0xFF3482FF),
            colorSpec = ThemeColorSpec.Spec2025,
            paletteStyle = ThemePaletteStyle.Vibrant,
            isDark = dark,
        )
    }

    MiuixTheme(controller = themeController) {
        val surface = MiuixTheme.colorScheme.surface
        val backdrop = rememberLayerBackdrop {
            drawRect(surface)
            drawContent()
        }
        val blurOverview = destination == AppDestination.Overview && !reduceMotion
        val blurColors = BlurDefaults.blurColors(
            blendColors = listOf(BlendColorEntry(surface.copy(alpha = 0.8f))),
        )
        Scaffold(
            topBar = {
                Box(
                    modifier = if (blurOverview) Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 25f,
                        colors = blurColors,
                    ) else Modifier,
                ) {
                    DriftTopBar(
                        destination = destination,
                        color = if (blurOverview) Color.Transparent else surface,
                    ) {
                        when (destination) {
                            AppDestination.NodeLibrary -> NodeSelectionActions(
                                category = category,
                                selectedIds = selectedIds,
                                onSelection = { selectedIds = it },
                            )
                            AppDestination.Settings -> HeaderAction(
                                label = "预览外观，返回概览",
                                icon = DriftIcons.Arrow,
                                onClick = {
                                    overviewState.requestScrollToItem(0)
                                    expanded = false
                                    destination = AppDestination.Overview
                                },
                            )
                            else -> Unit
                        }
                    }
                }
            },
            bottomBar = {
                DriftNavigationBar(destination = destination, onDestination = { destination = it })
            },
        ) { padding ->
            val page: @Composable (AppDestination) -> Unit = { tab ->
                Box(modifier = if (tab == AppDestination.Overview) Modifier else Modifier.padding(padding)) {
                    when (tab) {
                        AppDestination.Overview -> OverviewScreen(
                            modifier = if (blurOverview) Modifier.layerBackdrop(backdrop) else Modifier,
                            contentPadding = padding,
                            listState = overviewState,
                            expanded = expanded,
                            reduceMotion = reduceMotion,
                            onExpand = { expanded = !expanded },
                            onNodes = { destination = AppDestination.NodeLibrary },
                            onSettings = { destination = AppDestination.Settings },
                            onSubscriptions = { destination = AppDestination.Subscriptions },
                            onSpeedTest = { destination = AppDestination.SpeedTest },
                        )
                        AppDestination.Subscriptions -> SubscriptionsScreen(subscriptionsState)
                        AppDestination.SpeedTest -> SpeedTestScreen(
                            listState = speedTestState,
                            selectedIds = selectedIds,
                            onNodes = { destination = AppDestination.NodeLibrary },
                        )
                        AppDestination.NodeLibrary -> NodesScreen(
                            listState = nodesState,
                            category = category,
                            selectedIds = selectedIds,
                            onCategory = { category = it },
                            onSelection = { selectedIds = it },
                        )
                        AppDestination.Settings -> SettingsScreen(
                            listState = settingsState,
                            themeMode = themeMode,
                            reduceMotion = reduceMotion,
                            onThemeMode = { themeMode = it },
                            onReduceMotion = { reduceMotion = it },
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                    if (reduceMotion) {
                        page(destination)
                    } else {
                        Crossfade(
                            targetState = destination,
                            animationSpec = tween(180),
                            label = "bottom-navigation",
                        ) { page(it) }
                    }
                }
            }
        }
    }
}
