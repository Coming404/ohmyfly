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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    fingerprint = "$type|$server|$port|$extra",
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
