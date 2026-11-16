// 验证用量刷新失败保留，以及取消和删除时的状态隔离。
package com.ohmyfly.subscription

import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionUsageStoreTest {
    private val address = "https://sub.example/profile"
    private val settings = SubscriptionSettings(userAgent = "one")

    @Test
    fun refreshUpdatesUsageRetainsItOnFailureAndClearsItWhenSuccessfulResponseHasNone() = runTest {
        var content = "first"
        var header: String? = "upload=1;total=10;expire=1800000000"
        var status = HttpStatusCode.OK
        var requests = 0
        val client = subscriptionClient { request ->
            requests++
            assertEquals(HttpMethod.Get, request.method)
            respond(content, status, header?.let { headersOf(SubscriptionUsageHeader, it) } ?: Headers.Empty)
        }
        try {
            val store = TestCatalogStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode(it)) }))
            store.add(address, settings)
            assertEquals(SubscriptionUsage(upload = 1, total = 10, expireEpochSeconds = 1_800_000_000), store.towers.value.single().usage)
            content = "second"
            header = "download=2;total=20"
            store.refresh(address, settings)
            val successful = store.towers.value.single()
            assertEquals("second", successful.nodes.single().name)
            assertEquals(SubscriptionUsage(download = 2, total = 20), successful.usage)
            status = HttpStatusCode.NotFound
            header = "total=999"
            store.refresh(address, settings)
            assertEquals("服务器未找到订阅资源（HTTP 404）", store.towers.value.single().failure)
            assertEquals(successful.nodes, store.towers.value.single().nodes)
            assertEquals(successful.usage, store.towers.value.single().usage)
            status = HttpStatusCode.OK
            content = "recovered"
            header = null
            store.refresh(address, settings)
            assertNull(store.towers.value.single().failure)
            assertNull(store.towers.value.single().usage)
            assertEquals("recovered", store.towers.value.single().nodes.single().name)
            header = "total=0"
            store.refresh(address, settings)
            assertEquals(SubscriptionUsage(total = 0), store.towers.value.single().usage)
            header = "total=invalid"
            store.refresh(address, settings)
            assertNull(store.towers.value.single().usage)
            assertEquals(6, requests)
        } finally { client.close() }
    }

    @Test
    fun cancelledRefreshPreservesPreviouslyImportedNodesAndUsage() = runTest {
        var cancel = false
        val client = subscriptionClient {
            if (cancel) throw CancellationException("stop")
            respond("body", headers = headersOf(SubscriptionUsageHeader, "total=10"))
        }
        try {
            val store = TestCatalogStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) }))
            store.add(address, settings)
            val before = store.towers.value.single()
            cancel = true
            assertFailsWith<CancellationException> { store.refresh(address, settings) }
            assertEquals(before, store.towers.value.single())
        } finally { client.close() }
    }

    @Test
    fun deletingDuringRefreshDoesNotRestoreTowerOrItsUsage() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var paused = false
        val client = subscriptionClient {
            if (paused) { entered.complete(Unit); release.await() }
            respond("body", headers = headersOf(SubscriptionUsageHeader, "total=10"))
        }
        try {
            val store = TestCatalogStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) }))
            store.add(address, settings)
            assertEquals(SubscriptionUsage(total = 10), store.towers.value.single().usage)
            paused = true
            val refresh = launch { store.refresh(address, settings) }
            entered.await()
            store.remove(address)
            release.complete(Unit)
            refresh.join()
            assertTrue(store.towers.value.isEmpty())
        } finally { client.close() }
    }
}
