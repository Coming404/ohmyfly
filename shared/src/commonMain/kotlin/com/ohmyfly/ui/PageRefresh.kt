package com.ohmyfly.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.PullToRefresh

internal val LocalSettingsRefresh = staticCompositionLocalOf<suspend () -> Unit> {
    error("设置刷新入口未提供")
}

@Composable
internal fun PageRefresh(
    padding: PaddingValues,
    onRefresh: suspend () -> Unit,
    content: @Composable () -> Unit,
) {
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val messages = LocalAppMessages.current
    PullToRefresh(isRefreshing = refreshing, contentPadding = padding, onRefresh = {
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
