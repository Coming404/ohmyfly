package dev.ohmydrift.settings

internal data class SubscriptionSettings(
    val timeoutSeconds: Int = 15,
    val retries: Int = 2,
    val maxMiB: Int = 10,
    val userAgent: String = "",
    val includePattern: String = "",
    val excludePattern: String = "",
    val protocols: Set<String> = emptySet(),
)
