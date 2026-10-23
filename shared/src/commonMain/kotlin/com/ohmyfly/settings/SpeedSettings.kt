package com.ohmyfly.settings

internal data class SpeedSettings(
    val mode: SpeedMode = SpeedMode.Download,
    val latencyUrl: String = "https://www.gstatic.com/generate_204",
    val downloadUrl: String = "https://dl.google.com/chrome/mac/universal/stable/GGRO/googlechrome.dmg",
    val downloadMiB: Int = 10,
    val uploadMiB: Int = 5,
    val transferTimeoutSeconds: Int = 10,
    val latencyConcurrency: Int = 8,
    val latencyTimeoutSeconds: Int = 5,
    val latencySamples: Int = 6,
    val connectionsPerNode: Int = 4,
    val cellularConfirm: Boolean = true,
    val maxLatencyMs: Int = 1000,
    val maxFailurePercent: Int = 100,
    val minDownloadMiBps: Double = 0.0,
    val minUploadMiBps: Double = 0.0,
    val stopAfter: Int = 0,
    val renameExport: Boolean = false,
)
