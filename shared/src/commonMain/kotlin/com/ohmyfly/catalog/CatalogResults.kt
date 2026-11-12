package com.ohmyfly.catalog

import com.ohmyfly.settings.SpeedSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal suspend fun CatalogStore.createTask(settings: SpeedSettings, nodeIds: List<Long>): CatalogTask {
    val result = withContext(worker) {
        catalogJson.decodeFromJsonElement<CatalogTask>(call("taskCreate") {
            put("mode", settings.mode.name)
            put("settings", catalogJson.encodeToJsonElement(SpeedSettings.serializer(), settings))
            put("nodeIds", JsonArray(nodeIds.map { JsonPrimitive(it) }))
        })
    }
    measurementsChanged()
    return result
}

internal suspend fun CatalogStore.addTaskNodes(taskId: Long, nodeIds: List<Long>) = resultWrite("taskAddNodes") {
    put("id", taskId); put("nodeIds", JsonArray(nodeIds.map { JsonPrimitive(it) }))
}

internal suspend fun CatalogStore.updateTask(taskId: Long, status: String, reason: String = "") = resultWrite("taskUpdate") {
    put("id", taskId); put("status", status); put("reason", reason)
}

internal suspend fun CatalogStore.updateRun(runId: Long, status: String, reason: String = "") = resultWrite("runUpdate") {
    put("id", runId); put("status", status); put("reason", reason)
}

internal suspend fun CatalogStore.writeMetric(metric: CatalogMetric) = resultWrite("metricWrite") {
    put("metric", catalogJson.encodeToJsonElement(CatalogMetric.serializer(), metric))
}

internal suspend fun CatalogStore.writePlatform(result: CatalogPlatformResult) = resultWrite("platformWrite") {
    put("platform", catalogJson.encodeToJsonElement(CatalogPlatformResult.serializer(), result))
}

private suspend fun CatalogStore.resultWrite(action: String, request: JsonObjectBuilder.() -> Unit) {
    withContext(worker) { call(action, request) }
    measurementsChanged()
}

internal suspend fun readCatalogTask(taskId: Long): CatalogTask = withContext(Dispatchers.Default) {
    catalogJson.decodeFromJsonElement<CatalogTask>(catalogCall("task") { put("id", taskId) })
}

internal suspend fun readCatalogResults(nodeId: Long = 0, taskId: Long = 0, before: Long = 0, limit: Int = 100): CatalogResultPage =
    withContext(Dispatchers.Default) {
        catalogJson.decodeFromJsonElement<CatalogResultPage>(catalogCall("results") {
            put("nodeId", nodeId); put("taskId", taskId); put("anchor", before); put("limit", limit)
        })
    }
