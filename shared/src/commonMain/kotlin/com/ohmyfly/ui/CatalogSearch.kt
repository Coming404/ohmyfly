// 搜索框只在显式打开时请求焦点，页面切换与列表重建仅恢复查询内容。
package com.ohmyfly.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import top.yukonga.miuix.kmp.basic.SearchBarDefaults

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
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = SearchBarDefaults.InsideMargin.width, vertical = SearchBarDefaults.InsideMargin.height)
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
