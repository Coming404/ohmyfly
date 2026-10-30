package com.ohmyfly.startup

import kotlin.math.ceil

internal const val STARTUP_WINDOW_MILLIS = 20_000L
internal const val MAX_STARTUP_RECORDS = 10
internal const val STARTUP_RECORD_HEADER = "===== OhMyFly 启动记录 ====="

internal data class StartupFrameInterval(val endMillis: Double, val durationMillis: Double)

internal data class StartupFrameSummary(
    val frameCount: Int,
    val medianMillis: Double?,
    val p95Millis: Double?,
    val longFrames: List<StartupFrameInterval>,
    val longestFrames: List<StartupFrameInterval>,
)

internal fun summarizeStartupFrames(
    frameNanos: LongArray,
    elapsedMillis: DoubleArray,
    frameCount: Int,
    refreshRate: Double,
): StartupFrameSummary {
    val intervals = (1 until frameCount).map { index ->
        StartupFrameInterval(elapsedMillis[index], (frameNanos[index] - frameNanos[index - 1]) / 1_000_000.0)
    }
    val sorted = intervals.sortedBy { it.durationMillis }
    val count = sorted.size
    val median = if (count == 0) null else if (count % 2 == 0) {
        (sorted[count / 2 - 1].durationMillis + sorted[count / 2].durationMillis) / 2
    } else sorted[count / 2].durationMillis
    val p95 = if (count == 0) null else sorted[ceil(count * 0.95).toInt() - 1].durationMillis
    return StartupFrameSummary(
        frameCount = frameCount,
        medianMillis = median,
        p95Millis = p95,
        longFrames = intervals.filter { it.durationMillis > 1.5 * 1000 / refreshRate },
        longestFrames = sorted.takeLast(5).reversed(),
    )
}

internal fun retainStartupRecords(previous: String, record: String): String {
    val records = previous.split(STARTUP_RECORD_HEADER).drop(1).map {
        STARTUP_RECORD_HEADER + it.trimEnd()
    } + record.trimEnd()
    return records.takeLast(MAX_STARTUP_RECORDS).joinToString("\n\n", postfix = "\n")
}
