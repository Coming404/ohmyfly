package com.ohmyfly.catalog

import com.ohmyfly.settings.SubscriptionSettings
import com.ohmyfly.subscription.SubscriptionMirrorSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun CatalogStore.runBatch(
    settings: SubscriptionSettings,
    mirrors: SubscriptionMirrorSettings,
    action: suspend (CatalogBatchRunner) -> Unit,
) {
    val runner = batch(settings, mirrors)
    runningBatch = runner
    try { action(runner) }
    finally { if (runningBatch === runner) runningBatch = null }
}

internal fun CatalogStore.cancelTowerSync(id: Long) { runningBatch?.cancelTower(id) }

internal fun CatalogStore.cancelOperation() { operations.cancelAll() }

internal suspend fun CatalogStore.syncAll(settings: SubscriptionSettings, mirrors: SubscriptionMirrorSettings) =
    withContext(Dispatchers.Main) {
        runOperation("sync:all") {
            runBatch(settings, mirrors) { it.sync(listOf(0L)) { state.value.pendingRemoval } }
            check(state.value.taskFailed == 0) { "${state.value.taskFailed} 个来源同步失败，已保留上次成功数据" }
        }.await()
    }

internal fun CatalogStore.refresh(id: Long, settings: SubscriptionSettings, mirrors: SubscriptionMirrorSettings) =
    refreshTowers(setOf(id), settings, mirrors)

// 批量任务由目录队列持有；UI 只提交一次，不按卡片生命周期逐个等待。
internal fun CatalogStore.refreshTowers(
    ids: Set<Long>, settings: SubscriptionSettings, mirrors: SubscriptionMirrorSettings,
) = runOperation("sync:${ids.sorted().joinToString(",")}") {
    runBatch(settings, mirrors) { it.sync(ids) { state.value.pendingRemoval } }
    check(state.value.taskFailed == 0) { "${state.value.taskFailed} 个来源同步失败，已保留上次成功数据" }
}
