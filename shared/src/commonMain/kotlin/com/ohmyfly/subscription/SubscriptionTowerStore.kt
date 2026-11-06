package com.ohmyfly.subscription

import com.ohmyfly.nativecore.parseSubscriptionContent
import com.ohmyfly.settings.SubscriptionSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class SubscriptionTower(
    val address: String, val nodes: List<SubscriptionNode> = emptyList(), val failure: String? = null,
    val warnings: List<String> = emptyList(),
)

internal class SubscriptionTowerStore(
    private val importer: SubscriptionImporter = SubscriptionImporter(
        SubscriptionFetcher(createSubscriptionClient()), ::parseSubscriptionContent,
    ),
) {
    private val mutableTowers = MutableStateFlow<List<SubscriptionTower>>(emptyList())
    val towers: StateFlow<List<SubscriptionTower>> = mutableTowers.asStateFlow()

    /** 返回地址校验错误；导入失败信息通过 towers 提供。 */
    suspend fun add(address: String, settings: SubscriptionSettings): String? {
        validateSubscriptionUrl(address)?.let { return it }
        val target = address.trim()
        mutableTowers.update { list ->
            if (list.any { it.address == target }) list else list + SubscriptionTower(target)
        }
        refresh(target, settings)
        return null
    }

    suspend fun refresh(address: String, settings: SubscriptionSettings) {
        val target = address.trim()
        if (mutableTowers.value.none { it.address == target }) return
        val result = importer.importSubscription(target, settings)
        mutableTowers.update { list ->
            list.map { tower ->
                if (tower.address != target) tower else tower.copy(
                    // 失败刷新不丢失此前已导入的节点；删除中的塔台也不会被异步结果重新添加。
                    nodes = if (result.failure == null) result.nodes else tower.nodes,
                    failure = result.failure,
                    warnings = result.warnings,
                )
            }
        }
    }

    fun remove(address: String) {
        mutableTowers.update { list -> list.filterNot { it.address == address.trim() } }
    }
}
