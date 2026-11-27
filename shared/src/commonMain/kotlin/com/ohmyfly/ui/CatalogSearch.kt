// 搜索框只在显式打开时请求焦点，页面切换与列表重建仅恢复查询内容。
package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.layout.fillMaxWidth
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
internal fun CatalogSearch(
    query: String,
    label: String,
    state: CatalogSearchState,
    active: Boolean,
    onQuery: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    InputField(
        query = query,
        onQueryChange = onQuery,
        label = label,
        onSearch = { focus.clearFocus(); keyboard?.hide() },
        // Miuix 0.9.4 的 expanded 会主动聚焦，收起时还会清空查询；此处仅复用可编辑输入框。
        expanded = false,
        onExpandedChange = {},
        enabled = active,
        trailingIcon = {
            top.yukonga.miuix.kmp.basic.IconButton(
                onClick = hapticClick { state.close(); onQuery(""); focus.clearFocus(); keyboard?.hide() },
                enabled = active,
                modifier = Modifier.size(ChromeMetrics.ControlSize),
                backgroundColor = androidx.compose.ui.graphics.Color.Transparent, cornerRadius = AppCorners.Toolbar,
            ) {
                top.yukonga.miuix.kmp.basic.Icon(DriftIcons.Clear, "关闭搜索框",
                    modifier = Modifier.size(ChromeMetrics.ToolbarIconSize), tint = appIconTint())
            }
        },
        modifier = Modifier.fillMaxWidth()
            .focusProperties { canFocus = active }
            .focusRequester(requester),
    )
    LaunchedEffect(state.focusRequested, active) {
        if (state.focusRequested && active) {
            requester.requestFocus()
            state.consumeFocusRequest()
        }
    }
}
