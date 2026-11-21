// 主导航直接使用 Miuix 玻璃组件，页面状态和安全区仍由应用持有。
package com.ohmyfly.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.ui.glass.rememberGlassStyle
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem

/** 手势、选中胶囊和按压反馈交给库，移除应用内第二套导航物理实现。 */
@Composable
internal fun DriftNavigationBar(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    dark: Boolean,
) {
    val items = remember { AppDestination.entries.map { GlassNavigationItem(it.icon, it.label, it.label) } }
    val style = rememberGlassStyle(glassEnabled, dark)
    val select = hapticChange<Int> { onDestination(AppDestination.entries[it]) }
    Box(
        modifier = Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassNavigationBar(
            items = items,
            selectedIndex = destination.ordinal,
            onSelect = select,
            backdrop = backdrop.takeIf { glassEnabled },
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            defaultWindowInsetsPadding = false,
            shape = NavigationBarShape,
            style = style.value,
            stroke = if (glassEnabled) GlassNavigationBarDefaults.stroke() else null,
        )
    }
}
