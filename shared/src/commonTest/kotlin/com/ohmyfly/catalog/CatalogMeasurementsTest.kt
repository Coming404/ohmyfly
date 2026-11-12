package com.ohmyfly.catalog

import com.ohmyfly.settings.SpeedSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogMeasurementsTest {
    private class Bridge {
        var pageNode = 1L
        var version = 0L
        var filterToken = 6L
        var onPage: () -> Unit = {}
        var measurementFailure: String? = null
        var writeFailure = false
        var measurementCancellation = false
        val reads = mutableListOf<List<Long>>()
        var onRead: () -> Unit = {}

        fun call(raw: String): String {
            val request = catalogJson.parseToJsonElement(raw).jsonObject
            return when (request.getValue("action").jsonPrimitive.content) {
                "summary" -> """{"revision":1,"metricsRevision":0}"""
                "beginFilter" -> { filterToken++; """{"token":$filterToken,"revision":1}""" }
                "filterInputs" -> """{"inputs":[]}"""
                "commitFilter" -> """{"count":2}"""
                "page" -> {
                    onPage()
                    """{"revision":1,"filterToken":$filterToken,"metricsRevision":0,"matchCount":2,
                    "flights":[{"id":$pageNode,"name":"node","type":"ss","server":"example.test","port":443}]}"""
                }
                "taskCreate" -> if (writeFailure) """{"error":"write failed"}""" else {
                    version++
                    """{"id":42,"mode":"Latency","settings":{},"status":"queued","createdAt":1}"""
                }
                "metricWrite" -> if (writeFailure) """{"error":"write failed"}""" else { version++; """{"ok":true}""" }
                "measurements" -> {
                    val ids = request.getValue("nodeIds").jsonArray.map { it.jsonPrimitive.long }
                    reads += ids
                    if (measurementCancellation) throw CancellationException("cancelled read")
                    measurementFailure?.let { return it }
                    val response = """{"revision":1,"metricsRevision":$version,"nodes":[${ids.joinToString { id ->
                        """{"nodeId":$id,"platforms":[{"platform":"custom_stream","status":"unlocked","region":"JP","checkedAt":123}]}"""
                    }}]}"""
                    onRead()
                    response
                }
                else -> error("unexpected catalog call")
            }
        }
    }

    private fun TestScope.store(bridge: Bridge): CatalogStore {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = CatalogStore(StandardTestDispatcher(testScheduler), bridge::call, { "{}" })
        store.show("flights")
        advanceUntilIdle()
        assertEquals(listOf(1L), store.state.value.flights.map { it.id })
        return store
    }

    @Test
    fun committedTaskAndMetricSurviveIndependentRefreshFailure() = runTest {
        try {
            val bridge = Bridge()
            val store = store(bridge)
            for (failure in listOf("""{"error":"stale","stale":true}""", """{"error":"read failed"}""")) {
                bridge.measurementFailure = failure
                assertEquals(42L, store.createTask(SpeedSettings(), listOf(1)).id)
                store.writeMetric(CatalogMetric(1, "latency", "succeeded", 0.0, 1))
                advanceUntilIdle()
                assertTrue(store.state.value.measurementsPending)
                assertNotNull(store.state.value.measurementsFailure)
                assertNull(store.state.value.failure)
            }
            bridge.measurementFailure = null
            store.refreshMeasurements()
            assertFalse(store.state.value.measurementsPending)
            assertNull(store.state.value.measurementsFailure)
            assertEquals("custom_stream", store.state.value.flights.single().measurements.platforms.single().platform)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun oldPageResponseCannotPublishVersionForNewPage() = runTest {
        try {
            val bridge = Bridge()
            val store = store(bridge)
            bridge.version = 9
            bridge.onPage = { assertEquals(0L, store.state.value.summary.metricsRevision) }
            bridge.onRead = {
                bridge.onRead = {}
                bridge.pageNode = 2
                store.page("flights", 1)
            }
            store.refreshMeasurements()
            advanceUntilIdle()
            assertEquals(2L, store.state.value.flights.single().id)
            assertEquals(listOf(2L), bridge.reads.last())
            assertEquals(9L, store.state.value.summary.metricsRevision)
            assertEquals("JP", store.state.value.flights.single().measurements.platforms.single().region)
            assertFalse(store.state.value.measurementsPending)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun changedFilterWithSameNodeIdsStillRequiresFreshResponse() = runTest {
        try {
            val bridge = Bridge()
            val store = store(bridge)
            bridge.version = 5
            bridge.onRead = {
                bridge.onRead = {}
                store.updateFlightFilter(FlightFilter(includePattern = "node"))
            }
            store.refreshMeasurements()
            advanceUntilIdle()
            assertEquals(8L, store.state.value.filterToken)
            assertEquals(listOf(listOf(1L), listOf(1L)), bridge.reads)
            assertEquals(5L, store.state.value.summary.metricsRevision)
            assertFalse(store.state.value.measurementsPending)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun cancellationAndWriteErrorsStillPropagate() = runTest {
        try {
            val bridge = Bridge()
            val store = store(bridge)
            bridge.writeFailure = true
            assertFails { store.createTask(SpeedSettings(), listOf(1)) }
            assertTrue(bridge.reads.isEmpty())
            bridge.measurementCancellation = true
            assertFailsWith<CancellationException> { store.refreshMeasurements() }
            assertNull(store.state.value.measurementsFailure)
            val cancelled = launch(start = CoroutineStart.LAZY) { store.createTask(SpeedSettings(), listOf(1)) }
            cancelled.cancelAndJoin()
            assertTrue(cancelled.isCancelled)
        } finally { Dispatchers.resetMain() }
    }
}
