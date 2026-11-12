package com.ohmyfly.ui

import com.ohmyfly.catalog.CatalogMetric
import com.ohmyfly.catalog.CatalogMetricSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class FlightMetricTextTest {
    @Test
    fun failedLatestKeepsSuccessfulValueMarkedAsPrevious() {
        val success = CatalogMetric(1, "latency", "succeeded", value = 68.0)
        val failure = CatalogMetric(2, "latency", "failed")
        assertEquals("68 ms · 上次成功", compactMetric(CatalogMetricSummary(failure, success), "未测试"))
        assertEquals("失败", compactMetric(CatalogMetricSummary(failure), "未测试"))
    }

    @Test
    fun missingMeasurementsDoNotBecomeZero() {
        assertEquals("未测试", compactMetric(null, "未测试"))
        assertEquals("—", compactMetric(CatalogMetricSummary(), "—"))
    }
}
