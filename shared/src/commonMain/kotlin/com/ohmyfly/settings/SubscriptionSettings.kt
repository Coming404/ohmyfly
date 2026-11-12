package com.ohmyfly.settings

@kotlinx.serialization.Serializable
internal data class SubscriptionSettings(
    // 并发以来源任务和整份正文为单位，不是节点数。
    val towerConcurrency: Int = 4,
    val parseConcurrency: Int = 2,
    val addressLimit: Int = 200,
    val fileLimit: Int = 20,
    val timeoutSeconds: Int = 15,
    val retries: Int = 2,
    val maxMiB: Int = 10,
    val userAgent: String = "",
    val includePattern: String = "",
    val excludePattern: String = "剩余|到期|过期|流量|官网|套餐|expire|traffic",
    val outputDeduplication: com.ohmyfly.catalog.DeduplicationRule = com.ohmyfly.catalog.DeduplicationRule(),
    val protocols: Set<String> = emptySet(),
)
