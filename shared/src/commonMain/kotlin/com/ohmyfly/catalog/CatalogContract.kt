package com.ohmyfly.catalog

import com.ohmyfly.nativecore.NativeCoreException
import com.ohmyfly.subscription.SubscriptionUsage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

internal expect fun openNativeCatalog(): String
internal expect fun nativeCatalog(request: String): String

internal val catalogJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun catalogCall(action: String, block: JsonObjectBuilder.() -> Unit = {}): JsonObject {
    val request = buildJsonObject { put("action", action); block() }
    return decodeCatalog(nativeCatalog(request.toString()))
}

internal fun decodeCatalog(raw: String): JsonObject {
    val result = catalogJson.parseToJsonElement(raw).jsonObject
    result["error"]?.let {
        if (result["stale"]?.jsonPrimitive?.booleanOrNull == true) throw CatalogStaleException()
        throw NativeCoreException(it.jsonPrimitive.content)
    }
    return result
}

internal class CatalogStaleException : Exception("目录已更新，请重新加载")

@Serializable
internal data class CatalogSummary(val revision: Long = 0, val towers: Long = 0, val flights: Long = 0,
    val metricsRevision: Long = 0)

@Serializable
internal data class CatalogTower(
    val id: Long, val name: String, val nodeCount: Long, val failure: String,
    val sourceKind: String = "http", val sourceCount: Int = 1,
    val usage: SubscriptionUsage? = null,
    val warnings: JsonArray = JsonArray(emptyList()), val lastAttemptAt: Long? = null, val lastSuccessAt: Long? = null,
)

@Serializable
internal data class CatalogFlight(val id: Long, val name: String, val type: String, val server: String, val port: Int,
    val measurements: CatalogMeasurements = CatalogMeasurements(id),
    // 地区文字与国旗代码分离，CloudFlare 和未知地区没有国旗。
    val region: String = "未知", val regionCode: String = "", val enabled: Boolean = true)

@Serializable
internal data class CatalogPage(
    val revision: Long, val filterToken: Long = 0, val matchCount: Long = 0,
    val metricsRevision: Long = 0,
    val towers: List<CatalogTower> = emptyList(), val flights: List<CatalogFlight> = emptyList(),
)

// 去重规则只在 Go 提交事务内读取，刷新票据不重复传输规则集合。
@Serializable
internal data class RefreshTicket(val id: Long, val generation: Long, val address: String, val name: String)

internal data class CatalogState(
    val summary: CatalogSummary = CatalogSummary(), val towers: List<CatalogTower> = emptyList(),
    val flights: List<CatalogFlight> = emptyList(), val towerPage: Int = 0, val flightPage: Int = 0,
    val towerWindow: Map<Int, List<CatalogTower>> = emptyMap(),
    val flightWindow: Map<Int, List<CatalogFlight>> = emptyMap(),
    val ready: Boolean = false, val failure: String? = null, val busy: Boolean = false,
    val towerSearch: String = "", val towerMatchCount: Long? = null, val towerLoading: Boolean = true,
    val sourceTasks: Map<Long, CatalogSourceTask> = emptyMap(),
    val taskTotal: Int = 0, val taskCompleted: Int = 0, val taskFailed: Int = 0,
    val queuedBatches: Int = 0,
    val pendingRemoval: Set<Long> = emptySet(),
    val groups: CatalogGroups = CatalogGroups(),
    val flightFilter: FlightFilter = FlightFilter(), val filterError: String? = null,
    val appliedFilter: FlightFilter = FlightFilter(), val filterRevision: Long = 0,
    val filterToken: Long = 0, val matchCount: Long? = null, val flightLoading: Boolean = true,
    val protocols: Set<String> = emptySet(),
    val measurementsPending: Boolean = false, val measurementsFailure: String? = null,
)
