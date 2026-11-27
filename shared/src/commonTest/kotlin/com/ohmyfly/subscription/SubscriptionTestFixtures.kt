package com.ohmyfly.subscription

import com.ohmyfly.nativecore.NativeSubscription
import com.ohmyfly.nativecore.NativeSubscriptionNode
import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.*
import com.ohmyfly.catalog.*
import kotlinx.coroutines.flow.MutableStateFlow

internal fun testNode(
    name: String = "HK", type: String = "ss", server: String = "node.example", port: Int = 443,
    extra: JsonObject = JsonObject(emptyMap()),
): NativeSubscriptionNode = NativeSubscriptionNode(
    name, type, server, port,
    buildJsonObject {
        put("type", type)
        put("server", server)
        put("port", port)
        extra.forEach { (key, value) -> put(key, value) }
    },
    sourceFormat = "link", fromBase64 = false,
    contentHash = "$type|$server|$port|$extra",
)

internal fun testSnapshot(vararg nodes: NativeSubscriptionNode, skipped: Int = 0): NativeSubscription =
    NativeSubscription(nodes.toList(), nodes.size + skipped, nodes.size, skipped, 0, nodes.size, 0, 0, emptyMap(), emptyMap())

internal fun TestScope.subscriptionClient(handler: MockRequestHandler): HttpClient = HttpClient(MockEngine) {
    engine {
        dispatcher = StandardTestDispatcher(testScheduler)
        addHandler(handler)
    }
}

internal suspend fun TestScope.importSnapshots(
    vararg snapshots: NativeSubscription, settings: SubscriptionSettings = SubscriptionSettings(),
): SubscriptionImportResult {
    var requests = 0
    val client = subscriptionClient { respondOk((requests++ % snapshots.size).toString()) }
    val importer = SubscriptionImporter(SubscriptionFetcher(client), { snapshots[it.toInt()] })
    return try {
        importer.importSubscription("https://sub.example/profile", settings)
    } finally {
        client.close()
    }
}

// 仓储替身只实现目录事务协议；下载、过滤、名称与取消逻辑运行生产服务。
internal data class TestTower(
    val address: String, val nodes: List<SubscriptionNode> = emptyList(), val failure: String? = null,
    val warnings: List<String> = emptyList(), val usage: SubscriptionUsage? = null, val name: String = "",
)

internal class TestCatalogStore(importer: SubscriptionImporter) {
    private data class Row(var value: TestTower, var generation: Long = 0, var committed: Long = 0)
    private val rows = linkedMapOf<Long, Row>()
    private val staged = mutableMapOf<Long, MutableList<NativeSubscriptionNode>>()
    private var nextId = 1L
    val towers = MutableStateFlow<List<TestTower>>(emptyList())
    private var rules = SubscriptionSettings()
    private val service = CatalogRefreshService(importer, ::request)

    suspend fun add(address: String, settings: SubscriptionSettings, name: String = "",
        mirrors: SubscriptionMirrorSettings = SubscriptionMirrorSettings()): String? {
        rules = settings
        return service.add(address, settings, name, mirrors)
    }
    suspend fun refresh(address: String, settings: SubscriptionSettings,
        mirrors: SubscriptionMirrorSettings = SubscriptionMirrorSettings()) {
        rules = settings
        val id = rows.entries.firstOrNull { it.value.value.address == address.trim() }?.key ?: return
        service.refresh(id, settings, mirrors)
    }
    fun remove(address: String) {
        rows.entries.firstOrNull { it.value.value.address == address.trim() }?.key?.let { rows.remove(it); staged.remove(it) }
        publish()
    }
    private fun publish() { towers.value = rows.values.map { it.value } }
    private fun request(action: String, block: JsonObjectBuilder.() -> Unit): JsonObject {
        val input = buildJsonObject(block)
        val id = input["id"]?.jsonPrimitive?.long ?: 0L
        when (action) {
            "add" -> {
                val address = input.getValue("address").jsonPrimitive.content
                val supplied = input.getValue("name").jsonPrimitive.content
                val existing = rows.entries.firstOrNull { it.value.value.address == address }
                val key = existing?.key ?: nextId++
                if (existing == null) rows[key] = Row(TestTower(address, name = supplied))
                else if (existing.value.value.name.isBlank()) existing.value.value = existing.value.value.copy(name = supplied)
                publish()
                return buildJsonObject { put("id", key) }
            }
            "begin" -> {
                val row = rows.getValue(id); row.generation++; staged[id] = mutableListOf()
                return buildJsonObject {
                    put("id", id); put("generation", row.generation); put("address", row.value.address)
                    put("name", row.value.name); put("rules", catalogJson.encodeToJsonElement(SubscriptionSettings.serializer(), rules).toString())
                }
            }
            "status" -> return buildJsonObject {
                put("exists", rows.containsKey(id)); put("generation", rows[id]?.generation ?: 0)
                put("committed", rows[id]?.committed ?: 0)
            }
            "resolveName" -> {
                checkGeneration(id, input.getValue("generation").jsonPrimitive.long)
                val row = rows.getValue(id)
                row.value = row.value.copy(name = row.value.name.ifBlank { input.getValue("name").jsonPrimitive.content })
                publish()
            }
            "abort" -> staged.remove(id)
            "stage" -> {
                checkGeneration(id, input.getValue("generation").jsonPrimitive.long)
                staged.getValue(id).addAll(input.getValue("nodes").jsonArray.map { catalogJson.decodeFromJsonElement<NativeSubscriptionNode>(it) })
            }
            "fail" -> {
                checkGeneration(id, input.getValue("generation").jsonPrimitive.long)
                val row = rows.getValue(id)
                row.value = row.value.copy(failure = input.getValue("failure").jsonPrimitive.content,
                    name = row.value.name.ifBlank { input.getValue("name").jsonPrimitive.content })
                staged.remove(id); publish()
            }
            "commit" -> commit(input.getValue("refresh").jsonObject)
            else -> error("unexpected catalog operation: $action")
        }
        return buildJsonObject { put("ok", true) }
    }
    private fun checkGeneration(id: Long, generation: Long) {
        if (rows[id]?.generation != generation) throw CatalogStaleException()
    }
    private fun commit(value: JsonObject) {
        val id = value.getValue("id").jsonPrimitive.long
        val generation = value.getValue("generation").jsonPrimitive.long
        checkGeneration(id, generation)
        val row = rows.getValue(id)
        val records = staged.getValue(id)
        check(records.size == value.getValue("count").jsonPrimitive.int)
        val nodes = records.filter { it.eligible }.distinctBy { it.contentHash }.map {
            SubscriptionNode(it.name, it.type, it.server, it.port, it.fields)
        }
        row.value = row.value.copy(nodes = nodes, failure = null,
            name = row.value.name.ifBlank { value.getValue("name").jsonPrimitive.content },
            usage = value["usage"]?.takeIf { it != JsonNull }?.let { catalogJson.decodeFromJsonElement<SubscriptionUsage>(it) },
            warnings = value.getValue("warnings").jsonArray.map { it.jsonPrimitive.content })
        row.committed = generation
        staged.remove(id); publish()
    }
}
