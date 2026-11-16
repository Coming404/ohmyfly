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
    // 设置页默认直接填入这三个客户端 UA，并按该顺序尝试。
    val userAgent: String = "mihomo/1.19.33|v2rayN/7.20.4|clash-verge/v2.5.2",
    val includePattern: String = "",
    val excludePattern: String = "剩余|到期|过期|流量|官网|套餐|expire|traffic",
    val outputDeduplication: com.ohmyfly.catalog.DeduplicationRule = com.ohmyfly.catalog.DeduplicationRule(),
    val protocols: Set<String> = emptySet(),
)
