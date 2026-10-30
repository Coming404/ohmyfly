package com.ohmyfly.startup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StartupSummaryTest {
    private fun summary(intervals: List<Double>, refreshRate: Double = 60.0): StartupFrameSummary {
        val nanos = LongArray(intervals.size + 1)
        intervals.forEachIndexed { index, millis -> nanos[index + 1] = nanos[index] + (millis * 1_000_000).toLong() }
        val elapsed = DoubleArray(nanos.size) { nanos[it] / 1_000_000.0 }
        return summarizeStartupFrames(nanos, elapsed, nanos.size, refreshRate)
    }

    @Test
    fun evenMedianAndNearestRankP95UseIntervalsNotFrames() {
        val result = summary((1..20).map { it.toDouble() })
        assertEquals(21, result.frameCount)
        assertEquals(10.5, result.medianMillis)
        assertEquals(19.0, result.p95Millis)
        assertEquals(listOf(20.0, 19.0, 18.0, 17.0, 16.0), result.longestFrames.map { it.durationMillis })
    }

    @Test
    fun oddMedianAndSmallP95AreDefined() {
        val result = summary(listOf(10.0, 30.0, 20.0))
        assertEquals(20.0, result.medianMillis)
        assertEquals(30.0, result.p95Millis)
        assertEquals(3, result.longestFrames.size)
    }

    @Test
    fun longFramesStrictlyExceedOneAndHalfActualRefreshPeriods() {
        val at120Hz = summary(listOf(12.5, 13.0, 25.0), refreshRate = 120.0)
        assertEquals(listOf(13.0, 25.0), at120Hz.longFrames.map { it.durationMillis })
        assertEquals(listOf(25.5, 50.5), at120Hz.longFrames.map { it.endMillis })
        val at60Hz = summary(listOf(25.0, 26.0), refreshRate = 60.0)
        assertEquals(listOf(26.0), at60Hz.longFrames.map { it.durationMillis })
    }

    @Test
    fun fewerThanTwoFramesCannotProduceAnInterval() {
        val empty = summarizeStartupFrames(longArrayOf(), doubleArrayOf(), 0, 60.0)
        assertEquals(0, empty.frameCount)
        assertNull(empty.medianMillis)
        assertNull(empty.p95Millis)
        assertEquals(emptyList(), empty.longFrames)
        val single = summary(emptyList())
        assertEquals(1, single.frameCount)
        assertNull(single.medianMillis)
        assertNull(single.p95Millis)
    }

    @Test
    fun onlyNewestTenCompleteRecordsRemainInOrder() {
        val text = (1..12).fold("") { previous, index ->
            retainStartupRecords(previous, "$STARTUP_RECORD_HEADER\n记录 $index\n")
        }
        val records = text.split(STARTUP_RECORD_HEADER).drop(1).map { it.trim() }
        assertEquals((3..12).map { "记录 $it" }, records)
    }
}
