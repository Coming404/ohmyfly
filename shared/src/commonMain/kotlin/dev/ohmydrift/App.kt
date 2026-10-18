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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

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
    val colors = remember(dark) { if (dark) darkColorScheme() else lightColorScheme() }
    val items = remember {
        listOf(
            "概览" to DriftIcons.Overview,
            "节点" to DriftIcons.Nodes,
            "外观" to DriftIcons.Theme,
        )
    }

    MiuixTheme(colors = colors) {
        Scaffold(
            topBar = { SmallTopAppBar(title = "ohmydrift") },
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
                when (tab) {
                    0 -> OverviewScreen(
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
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
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
