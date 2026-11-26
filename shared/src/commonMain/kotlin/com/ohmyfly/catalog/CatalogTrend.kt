package com.ohmyfly.catalog

import com.ohmyfly.logging.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun CatalogStore.recordTrend(summary: CatalogSummary) {
    if (lastTrendObservation == summary) return
    lastTrendObservation = summary
    trendWriteJob?.cancel()
    trendWriteJob = scope.launch(worker) {
        try {
            call("recordTrend") {
                put("observation", buildJsonObject {
                    put("revision", summary.revision)
                    put("flights", summary.flights)
                    put("usedTrafficBytes", summary.usedTrafficBytes)
                    put("fuelReported", summary.fuelReported)
                })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (lastTrendObservation == summary) lastTrendObservation = null
            logger.debug { "[目录] 保存每日趋势失败：${catalogFailure(error)}" }
        }
    }
}
