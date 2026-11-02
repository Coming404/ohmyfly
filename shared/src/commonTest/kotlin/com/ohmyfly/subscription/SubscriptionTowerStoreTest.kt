package com.ohmyfly.subscription

import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionTowerStoreTest {
    private val settings = SubscriptionSettings(userAgent = "one")

    @Test
    fun invalidAddressIsRejectedBeforeChangingStateOrRequesting() = runTest {
        var requests = 0
        val client = subscriptionClient { requests++; respondOk("p") }
        try {
            val store = SubscriptionTowerStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) }))
            assertEquals("只支持 HTTP 或 HTTPS 地址", store.add("ftp://sub.example/p", settings))
            assertTrue(store.towers.value.isEmpty())
            assertEquals(0, requests)
        } finally { client.close() }
    }

    @Test
    fun addNormalizesAddressAndPublishesImportedNodeFields() = runTest {
        val client = subscriptionClient { respondOk("p") }
        try {
            val store = SubscriptionTowerStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) }))
            assertNull(store.add("  https://sub.example/p  ", settings))
            assertEquals(SubscriptionTower("https://sub.example/p", listOf(SubscriptionNode("HK", "ss", "node.example", 443))), store.towers.value.single())
        } finally { client.close() }
    }

    @Test
    fun refreshReplacesNodesRetainsThemOnFailureAndClearsFailureOnRecovery() = runTest {
        var content = "first"
        val client = subscriptionClient {
            if (content == "fail") respond("", HttpStatusCode.NotFound) else respondOk(content)
        }
        try {
            val store = SubscriptionTowerStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode(it)) }))
            store.add("https://sub.example/p", settings)
            content = "second"
            store.refresh("https://sub.example/p", settings)
            assertEquals("second", store.towers.value.single().nodes.single().name)
            content = "fail"
            store.refresh("https://sub.example/p", settings)
            assertEquals("HTTP 404", store.towers.value.single().failure)
            assertEquals("second", store.towers.value.single().nodes.single().name)
            content = "recovered"
            store.refresh("https://sub.example/p", settings)
            assertNull(store.towers.value.single().failure)
            assertEquals("recovered", store.towers.value.single().nodes.single().name)
        } finally { client.close() }
    }

    @Test
    fun failedInitialImportRemainsInTheTowerListWithFailureInformation() = runTest {
        val client = subscriptionClient { respond("", HttpStatusCode.NotFound) }
        try {
            val store = SubscriptionTowerStore(SubscriptionImporter(SubscriptionFetcher(client), { error("must not parse") }))
            assertNull(store.add("https://sub.example/p", settings))
            assertEquals(SubscriptionTower("https://sub.example/p", failure = "HTTP 404"), store.towers.value.single())
        } finally { client.close() }
    }

    @Test
    fun removeOnlyDeletesTheRequestedAddress() = runTest {
        val client = subscriptionClient { respondOk("p") }
        try {
            val store = SubscriptionTowerStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) }))
            store.add("https://sub.example/one", settings)
            store.add("https://sub.example/two", settings)
            store.remove(" https://sub.example/one ")
            assertEquals(listOf("https://sub.example/two"), store.towers.value.map { it.address })
        } finally { client.close() }
    }

    @Test
    fun deletingDuringRefreshDoesNotRestoreTheDeletedTower() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var paused = false
        val client = subscriptionClient {
            if (paused) { entered.complete(Unit); release.await() }
            respondOk("p")
        }
        try {
            val store = SubscriptionTowerStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) }))
            store.add("https://sub.example/p", settings)
            paused = true
            val refresh = launch { store.refresh("https://sub.example/p", settings) }
            entered.await()
            store.remove("https://sub.example/p")
            release.complete(Unit)
            refresh.join()
            assertTrue(store.towers.value.isEmpty())
        } finally { client.close() }
    }

    @Test
    fun addingTheSameNormalizedAddressRefreshesTheExistingTower() = runTest {
        var requests = 0
        val client = subscriptionClient { respondOk((++requests).toString()) }
        try {
            val store = SubscriptionTowerStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode(it)) }))
            store.add("https://sub.example/p", settings)
            store.add(" https://sub.example/p ", settings)
            assertEquals(1, store.towers.value.size)
            assertEquals("2", store.towers.value.single().nodes.single().name)
        } finally { client.close() }
    }
}
