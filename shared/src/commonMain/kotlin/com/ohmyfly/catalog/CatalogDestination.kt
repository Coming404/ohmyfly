package com.ohmyfly.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ohmyfly.ui.AppDestination
import com.ohmyfly.ui.AppMessageKind
import com.ohmyfly.ui.LocalAppMessages

@Composable
internal fun CatalogDestination(destination: AppDestination, revision: Long, clearSelection: () -> Unit) {
    val messages = LocalAppMessages.current
    // 在应用根部消费结果，关闭添加抽屉或切页不会丢失完成通知。
    LaunchedEffect(Unit) {
        for ((total, failed) in appCatalog.syncResults) {
            if (total > 0) messages.show(
                if (failed == 0) AppMessageKind.Success else AppMessageKind.Failure,
                if (failed == 0) "$total 个来源同步完成" else "同步完成：${total - failed} 个成功，$failed 个失败，请查看塔台状态",
            )
        }
    }
    LaunchedEffect(destination) {
        clearSelection()
        appCatalog.show(when (destination) {
            AppDestination.Subscriptions -> "towers"
            AppDestination.NodeLibrary -> "flights"
            else -> null
        })
    }
    LaunchedEffect(revision) { clearSelection() }
}
