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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import dev.ohmydrift.ui.AppearanceScreen
import dev.ohmydrift.ui.DriftIcons
import dev.ohmydrift.ui.NodesScreen
import dev.ohmydrift.ui.OverviewScreen
import dev.ohmydrift.ui.ThemeMode
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
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
    var selectedTab by remember { mutableIntStateOf(0) }
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    var reduceMotion by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("全部") }
    var selectedIds by remember { mutableStateOf(setOf("tokyo")) }
    val overviewState = rememberLazyListState()
    val nodesState = rememberLazyListState()
    val appearanceState = rememberLazyListState()
    val dark = themeMode.isDark(isSystemInDarkTheme())
    val themeController = remember(dark) {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.MonetSystem,
            // An explicit seed makes Expressive / Spec2025 effective on both platforms.
            keyColor = Color(0xFF3482FF),
            colorSpec = ThemeColorSpec.Spec2025,
            paletteStyle = ThemePaletteStyle.Expressive,
            isDark = dark,
        )
    }
    val items = remember {
        listOf(
            "概览" to DriftIcons.Overview,
            "节点" to DriftIcons.Nodes,
            "外观" to DriftIcons.Theme,
        )
    }

    MiuixTheme(controller = themeController) {
        val surface = MiuixTheme.colorScheme.surface
        val backdrop = rememberLayerBackdrop {
            drawRect(surface)
            drawContent()
        }
        val blurOverview = selectedTab == 0 && !reduceMotion
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
                    SmallTopAppBar(title = "ohmydrift", color = if (blurOverview) Color.Transparent else surface)
                }
            },
            bottomBar = {
                NavigationBar {
                    items.forEachIndexed { index, (label, icon) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = icon,
                            label = label,
                        )
                    }
                }
            },
        ) { padding ->
            val page: @Composable (Int) -> Unit = { tab ->
                Box(modifier = if (tab == 0) Modifier else Modifier.padding(padding)) {
                    when (tab) {
                        0 -> OverviewScreen(
                            modifier = if (blurOverview) Modifier.layerBackdrop(backdrop) else Modifier,
                            contentPadding = padding,
                            listState = overviewState,
                            expanded = expanded,
                            reduceMotion = reduceMotion,
                            onExpand = { expanded = !expanded },
                            onNodes = { selectedTab = 1 },
                            onAppearance = { selectedTab = 2 },
                        )
                        1 -> NodesScreen(
                            listState = nodesState,
                            category = category,
                            selectedIds = selectedIds,
                            onCategory = { category = it },
                            onSelection = { selectedIds = it },
                        )
                        2 -> AppearanceScreen(
                            listState = appearanceState,
                            themeMode = themeMode,
                            reduceMotion = reduceMotion,
                            onThemeMode = { themeMode = it },
                            onReduceMotion = { reduceMotion = it },
                            onPreview = {
                                overviewState.requestScrollToItem(0)
                                expanded = false
                                selectedTab = 0
                            },
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
                        page(selectedTab)
                    } else {
                        Crossfade(
                            targetState = selectedTab,
                            animationSpec = tween(180),
                            label = "bottom-navigation",
                        ) { page(it) }
                    }
                }
            }
        }
    }
}
