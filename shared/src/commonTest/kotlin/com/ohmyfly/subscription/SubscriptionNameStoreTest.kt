// 验证空名补全、已有名称保留，以及失败和重定向时的原域名回退。
package com.ohmyfly.subscription

import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionNameStoreTest {
    private val address = "https://sub.example/profile?token=private"
    private val settings = SubscriptionSettings(userAgent = "one")

    @Test
    fun suppliedNonblankNameIsPreservedVerbatimAcrossAddAndRefresh() = runTest {
        val client = subscriptionClient { respond("body", headers = headersOf("profile-title", "Remote")) }
        try {
            val store = TestCatalogStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) }))
            store.add(address, settings, name = "  My title.yaml  ")
            assertEquals("  My title.yaml  ", store.towers.value.single().name)
            store.refresh(address, settings)
            store.add(address, settings, name = "Different supplied name")
            assertEquals("  My title.yaml  ", store.towers.value.single().name)
        } finally { client.close() }
    }

    @Test
    fun blankNameUsesFirstSuccessfulRemoteNameAndLaterRefreshDoesNotRenameIt() = runTest {
        var title = "  First.yaml  "
        var requests = 0
        val client = subscriptionClient { request ->
            requests++
            assertEquals(HttpMethod.Get, request.method)
            respond("body", headers = headersOf("profile-title", title))
        }
        try {
            val store = TestCatalogStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) }))
            store.add(address, settings, name = " \t ")
            assertEquals("First.yaml", store.towers.value.single().name)
            title = "Second"
            store.refresh(address, settings)
            assertEquals("First.yaml", store.towers.value.single().name)
            assertEquals(2, requests)
        } finally { client.close() }
    }

    @Test
    fun emptyOrInvalidHeadersFallBackToOriginalHostAfterRedirect() = runTest {
        for (title in listOf(null, " ", "base64:!!!")) {
            val hosts = mutableListOf<String>()
            val client = subscriptionClient { request ->
                hosts += request.url.host
                if (request.url.host == "origin.example") {
                    respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://cdn.example/download.yaml"))
                } else {
                    respond("body", headers = title?.let { headersOf("profile-title", it) } ?: Headers.Empty)
                }
            }
            try {
                val store = TestCatalogStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) }))
                store.add("https://user:secret@origin.example/private.yaml?token=hidden", settings)
                assertEquals("origin.example", store.towers.value.single().name)
                assertEquals(listOf("origin.example", "cdn.example"), hosts)
            } finally { client.close() }
        }
    }

    @Test
    fun initialFailurePreservesSuppliedNameOrFillsHostAndRecoveryDoesNotOverwriteIt() = runTest {
        for (name in listOf("", "  Personal  ")) {
            var status = HttpStatusCode.NotFound
            val client = subscriptionClient { respond("body", status, headersOf("profile-title", "Remote")) }
            try {
                val store = TestCatalogStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) }))
                store.add(address, settings, name)
                val expected = if (name.isEmpty()) "sub.example" else name
                assertEquals(expected, store.towers.value.single().name)
                assertEquals("HTTP 404", store.towers.value.single().failure)
                status = HttpStatusCode.OK
                store.refresh(address, settings)
                assertNull(store.towers.value.single().failure)
                assertEquals(expected, store.towers.value.single().name)
            } finally { client.close() }
        }
    }

    @Test
    fun failedRefreshKeepsNameNodesAndUsageTogether() = runTest {
        var status = HttpStatusCode.OK
        var content = "first"
        val client = subscriptionClient {
            respond(content, status, headersOf(
                "profile-title" to listOf(content), SubscriptionUsageHeader to listOf("total=10"),
            ))
        }
        try {
            val store = TestCatalogStore(SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode(it)) }))
            store.add(address, settings)
            val before = store.towers.value.single()
            content = "error"
            status = HttpStatusCode.NotFound
            store.refresh(address, settings)
            val after = store.towers.value.single()
            assertEquals("HTTP 404", after.failure)
            assertEquals(before.name, after.name)
            assertEquals(before.nodes, after.nodes)
            assertEquals(before.usage, after.usage)
        } finally { client.close() }
    }
}
