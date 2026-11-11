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
internal data class CatalogSummary(val revision: Long = 0, val towers: Long = 0, val flights: Long = 0)

@Serializable
internal data class CatalogTower(
    val id: Long, val name: String, val nodeCount: Long, val failure: String,
    val usage: SubscriptionUsage? = null,
)

@Serializable
internal data class CatalogFlight(val id: Long, val name: String, val type: String, val server: String, val port: Int)

@Serializable
internal data class CatalogPage(
    val revision: Long, val filterToken: Long = 0, val matchCount: Long = 0,
    val towers: List<CatalogTower> = emptyList(), val flights: List<CatalogFlight> = emptyList(),
)

@Serializable
internal data class RefreshTicket(val id: Long, val generation: Long, val address: String, val name: String,
    val dedupRules: DeduplicationRule = DeduplicationRule())

internal data class CatalogState(
    val summary: CatalogSummary = CatalogSummary(), val towers: List<CatalogTower> = emptyList(),
    val flights: List<CatalogFlight> = emptyList(), val towerPage: Int = 0, val flightPage: Int = 0,
    val ready: Boolean = false, val failure: String? = null, val busy: Boolean = false,
    val flightFilter: FlightFilter = FlightFilter(), val filterError: String? = null,
    val appliedFilter: FlightFilter = FlightFilter(), val filterRevision: Long = 0,
    val filterToken: Long = 0, val matchCount: Long? = null, val flightLoading: Boolean = true,
    val protocols: Set<String> = emptySet(),
)
