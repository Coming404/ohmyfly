// 验证真实镜像开关、候选回退和原地址元数据归属。
package com.ohmyfly.subscription

import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

internal val mirrorSubscriptionUrl = "https://raw.githubusercontent.com/o/r/main/nodes.yaml"
internal val subscriptionMirrors = SubscriptionMirrorSettings(true, listOf(
    MirrorEntry("a", "A", "https://a.example/"), MirrorEntry("b", "B", "https://b.example/"),
), "b")
internal fun TestScope.mirroredSubscriptionClient(handler: MockRequestHandler): HttpClient = HttpClient(MockEngine) {
    followRedirects = false
    engine { dispatcher = StandardTestDispatcher(testScheduler); addHandler(handler) }
}
internal fun mirrorSubscriptionFetcher(client: HttpClient) = SubscriptionFetcher(client, { client }, {})

class SubscriptionMirrorTest {
    private val settings = SubscriptionSettings(userAgent = "custom-one|custom-two", retries = 0)

    @Test
    fun disabledOrEmptyMirrorsGoDirectAndConfigurationChangesApplyOnRefresh() = runTest {
        val hosts = mutableListOf<String>()
        val client = mirroredSubscriptionClient { request -> hosts += request.url.host; respond("nodes") }
        try {
            val store = TestCatalogStore(SubscriptionImporter(mirrorSubscriptionFetcher(client), { testSnapshot(testNode()) }))
            store.add(mirrorSubscriptionUrl, settings, mirrors = subscriptionMirrors.copy(enabled = false))
            store.refresh(mirrorSubscriptionUrl, settings, subscriptionMirrors)
            store.refresh(mirrorSubscriptionUrl, settings, subscriptionMirrors.copy(mirrors = emptyList()))
            assertEquals(listOf("raw.githubusercontent.com", "b.example", "raw.githubusercontent.com"), hosts)
        } finally { client.close() }
    }

    @Test
    fun selectedMirrorSuccessStopsAndPreservesSameResponseMetadataAndOriginalMozilla() = runTest {
        val hosts = mutableListOf<String>()
        val client = mirroredSubscriptionClient { request ->
            hosts += request.url.host
            assertEquals(HttpMethod.Get, request.method)
            assertEquals(MozillaSubscriptionUserAgent, request.headers[HttpHeaders.UserAgent])
            respond("nodes", headers = headersOf("profile-title" to listOf("Mirror title"),
                SubscriptionUsageHeader to listOf("total=8")))
        }
        try {
            val importer = SubscriptionImporter(mirrorSubscriptionFetcher(client), { assertEquals("nodes", it); testSnapshot(testNode()) })
            val result = importer.importSubscription(mirrorSubscriptionUrl, settings, mirrors = subscriptionMirrors)
            assertNull(result.failure)
            assertEquals("Mirror title", result.name)
            assertEquals(SubscriptionUsage(total = 8), result.usage)
            assertEquals(listOf("b.example"), hosts)
            val skipped = importer.importSubscription(mirrorSubscriptionUrl, settings, resolveName = false, mirrors = subscriptionMirrors)
            assertNull(skipped.name)
            assertEquals(SubscriptionUsage(total = 8), skipped.usage)
        } finally { client.close() }
    }

    @Test
    fun retryableFailureRetriesSameRouteThenOtherMirrorsThenDirect() = runTest {
        val hosts = mutableListOf<String>()
        var emptyResponse = false
        val client = mirroredSubscriptionClient { request ->
            hosts += request.url.host
            when (request.url.host) {
                "b.example" -> respond("", HttpStatusCode.ServiceUnavailable)
                "a.example" -> if (emptyResponse) respond("empty") else respond("", HttpStatusCode.NotFound)
                else -> respond("nodes")
            }
        }
        try {
            val importer = SubscriptionImporter(mirrorSubscriptionFetcher(client), {
                if (it == "empty") testSnapshot() else testSnapshot(testNode())
            })
            for (empty in listOf(false, true)) {
                emptyResponse = empty
                hosts.clear()
                val result = importer.importSubscription(mirrorSubscriptionUrl, settings.copy(retries = 1), mirrors = subscriptionMirrors)
                assertNull(result.failure)
                assertEquals(1, result.nodes.size)
                assertEquals(listOf("b.example", "b.example", "a.example", "raw.githubusercontent.com"), hosts)
            }
        } finally { client.close() }
    }

    @Test
    fun cancellationStopsBeforeAnyFallback() = runTest {
        val hosts = mutableListOf<String>()
        val client = mirroredSubscriptionClient { request -> hosts += request.url.host; throw CancellationException("stop") }
        try {
            val importer = SubscriptionImporter(mirrorSubscriptionFetcher(client), { error("must not parse") })
            assertFailsWith<CancellationException> {
                importer.importSubscription(mirrorSubscriptionUrl, settings, mirrors = subscriptionMirrors)
            }
            assertEquals(listOf("b.example"), hosts)
        } finally { client.close() }
    }

    @Test
    fun nonGithubHtmlQueryAndCredentialsAreNeverForwarded() = runTest {
        for (url in listOf("https://sub.example/subscribe?token=private", "$mirrorSubscriptionUrl?token=private",
            "https://user:pass@raw.githubusercontent.com/o/r/main/nodes", "https://gist.github.com/u/id",
            "https://github.com.evil/o/r/raw/main/nodes", "https://example.com/github.com/o/r/raw/nodes")) {
            val hosts = mutableListOf<String>()
            val client = mirroredSubscriptionClient { request -> hosts += request.url.host; respond("nodes") }
            try {
                val importer = SubscriptionImporter(mirrorSubscriptionFetcher(client), { testSnapshot(testNode()) })
                assertNull(importer.importSubscription(url, settings.copy(userAgent = "one"), mirrors = subscriptionMirrors).failure)
                assertEquals(1, hosts.size)
                assertEquals(io.ktor.http.Url(url).host, hosts.single())
            } finally { client.close() }
        }
    }

    @Test
    fun originalDomainFallbackAndLargeBodyArePreservedThroughMirror() = runTest {
        val body = "n".repeat(70_000)
        val client = mirroredSubscriptionClient { respond(body) }
        try {
            val store = TestCatalogStore(SubscriptionImporter(mirrorSubscriptionFetcher(client), {
                assertEquals(body, it); testSnapshot(testNode())
            }))
            store.add(mirrorSubscriptionUrl, settings.copy(maxMiB = 1), mirrors = subscriptionMirrors)
            val tower = store.towers.value.single()
            assertNull(tower.failure)
            assertEquals(mirrorSubscriptionUrl, tower.address)
            assertEquals("raw.githubusercontent.com", tower.name)
        } finally { client.close() }
    }
}
