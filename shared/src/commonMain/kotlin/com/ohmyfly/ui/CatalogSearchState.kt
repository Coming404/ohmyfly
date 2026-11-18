// 搜索框显示状态归页面所有；聚焦是一次性操作，不能随页面或懒列表重建重复执行。
package com.ohmyfly.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class CatalogSearchState {
    var expanded by mutableStateOf(false)
        private set
    var focusRequested by mutableStateOf(false)
        private set

    // 只有用户主动打开搜索入口才产生聚焦请求。
    fun open() {
        expanded = true
        focusRequested = true
    }

    fun close() {
        expanded = false
        focusRequested = false
    }

    fun consumeFocusRequest() {
        focusRequested = false
    }

    // 导航只收起空搜索框，不改动已输入的查询和筛选结果。
    fun leave(query: String) {
        focusRequested = false
        if (query.isBlank()) expanded = false
    }
}
