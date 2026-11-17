package com.ohmyfly.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState

internal val LocalSettingsRefresh = staticCompositionLocalOf<suspend () -> Unit> {
    error("设置刷新入口未提供")
}

@Composable
internal fun PageRefresh(
    padding: PaddingValues,
    onRefresh: suspend () -> Unit,
    refreshingText: String = "正在刷新页面",
    content: @Composable () -> Unit,
) {
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val messages = LocalAppMessages.current
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.35f)
    PullToRefresh(isRefreshing = refreshing, contentPadding = padding,
        pullToRefreshState = refreshState,
        refreshTexts = listOf("向下拉刷新", "松开立即刷新", refreshingText, "刷新完成"), onRefresh = {
        if (!refreshing) {
            refreshing = true
            scope.launch {
                try {
                    onRefresh()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    val reason = com.ohmyfly.catalog.logCatalogFailure("下拉刷新", error)
                    messages.show(AppMessageKind.Failure, "刷新失败：$reason")
                } finally {
                    refreshing = false
                }
            }
        }
    }, content = content)
}
