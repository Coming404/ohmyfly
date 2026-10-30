package com.ohmyfly.settings

internal data class MediaSettings(
    val enabled: Boolean = false,
    val platforms: Set<String> = setOf("netflix", "disney", "youtube", "tiktok", "openai", "gemini", "copilot"),
    val concurrency: Int = 2,
    val timeoutSeconds: Int = 10,
    val retries: Int = 1,
)
