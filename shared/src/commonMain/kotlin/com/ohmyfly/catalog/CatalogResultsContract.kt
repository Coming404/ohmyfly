package com.ohmyfly.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class CatalogMetric(
    val runId: Long, val kind: String, val status: String, val value: Double? = null,
    val measuredAt: Long? = null, val reason: String = "",
)

@Serializable
internal data class CatalogPlatformResult(
    val runId: Long, val platform: String, val status: String, val region: String = "",
    val checkedAt: Long? = null, val reason: String = "",
)

@Serializable
internal data class CatalogMetricSummary(val latest: CatalogMetric? = null, val valid: CatalogMetric? = null)

@Serializable
internal data class CatalogNodeRun(
    val id: Long, val taskId: Long, val nodeId: Long, val status: String, val taskStatus: String,
    val startedAt: Long? = null, val finishedAt: Long? = null, val reason: String = "",
    val metrics: List<CatalogMetric> = emptyList(), val platforms: List<CatalogPlatformResult> = emptyList(),
)

@Serializable
internal data class CatalogPlatformSummary(
    val platform: String, val status: String, val region: String = "", val checkedAt: Long? = null,
)

@Serializable
internal data class CatalogMeasurements(
    val nodeId: Long, val latestRun: CatalogNodeRun? = null,
    val metrics: Map<String, CatalogMetricSummary> = emptyMap(), val platforms: List<CatalogPlatformSummary> = emptyList(),
)

@Serializable
internal data class CatalogMeasurementBatch(
    val revision: Long, val metricsRevision: Long, val nodes: List<CatalogMeasurements>,
)

@Serializable
internal data class CatalogTask(
    val id: Long, val mode: String, val settings: JsonObject, val status: String,
    val createdAt: Long, val startedAt: Long? = null, val finishedAt: Long? = null, val reason: String = "",
)

@Serializable
internal data class CatalogResultPage(val metricsRevision: Long, val runs: List<CatalogNodeRun>)
