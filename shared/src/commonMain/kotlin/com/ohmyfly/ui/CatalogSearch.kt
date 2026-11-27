// 搜索框只在显式打开时请求焦点，页面切换与列表重建仅恢复查询内容。
package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import top.yukonga.miuix.kmp.basic.SearchBar
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import top.yukonga.miuix.kmp.basic.InputField

@Composable
internal fun rememberSearchVisibility(expanded: Boolean): androidx.compose.animation.core.MutableTransitionState<Boolean> =
    remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = expanded }

@Composable
internal fun CatalogSearchVisibility(
    visibility: androidx.compose.animation.core.MutableTransitionState<Boolean>,
    content: @Composable (Boolean) -> Unit,
) {
    AnimatedVisibility(visibleState = visibility,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()) {
        // 等输入框尺寸稳定后再聚焦，避免键盘与展开动画争抢布局。
        content(visibility.isIdle && visibility.currentState)
    }
}

@Composable
internal fun CatalogSearch(
    query: String,
    label: String,
    state: CatalogSearchState,
    active: Boolean,
    focusReady: Boolean,
    onClear: () -> Unit,
    onQuery: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    val cancel = { state.close(); focus.clearFocus(); keyboard?.hide(); Unit }
    SearchBar(
        expanded = state.expanded && active,
        onExpandedChange = { if (!it) cancel() },
        insideMargin = DpSize.Zero,
        outsideEndAction = { CatalogTextButton("取消", onClick = hapticClick(cancel)) },
        inputField = {
            InputField(
                query = query,
                onQueryChange = onQuery,
                label = label,
                onSearch = { focus.clearFocus(); keyboard?.hide() },
                // 内置展开会自动聚焦与清空查询，由页面的一次性请求接管焦点生命周期。
                expanded = false,
                onExpandedChange = {},
                enabled = active,
                trailingIcon = {
                    top.yukonga.miuix.kmp.basic.IconButton(
                        onClick = hapticClick(onClear),
                        enabled = active,
                        modifier = Modifier.size(ChromeMetrics.ControlSize),
                        backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
                        cornerRadius = AppCorners.Toolbar,
                    ) {
                        top.yukonga.miuix.kmp.basic.Icon(DriftIcons.Clear, "清除筛选",
                            modifier = Modifier.size(ChromeMetrics.ToolbarIconSize), tint = appIconTint())
                    }
                },
                modifier = Modifier.fillMaxWidth()
                    .focusProperties { canFocus = active }
                    .focusRequester(requester),
            )
        },
        content = {},
    )
    LaunchedEffect(state.focusRequested, active, focusReady) {
        if (state.focusRequested && active && focusReady) {
            requester.requestFocus()
            state.consumeFocusRequest()
        }
    }
}
