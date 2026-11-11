// 验证 provider 独立路由、敏感请求头隔离及镜像候选共用总预算。
package com.ohmyfly.subscription

import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class SubscriptionMirrorBoundariesTest {
    private val settings = SubscriptionSettings(userAgent = "one", retries = 0, timeoutSeconds = 3)
    private class Clock(private val now: () -> Long) : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
        override fun read(): Long = now()
    }

    @Test
    fun providersChooseRoutingFromTheirOwnUrlAndDoNotSupplyRootMetadata() = runTest {
        for (rootIsGithub in listOf(false, true)) {
            val root = if (rootIsGithub) mirrorSubscriptionUrl else "https://sub.example/root"
            val child = if (rootIsGithub) "https://child.example/nodes" else mirrorSubscriptionUrl
            val hosts = mutableListOf<String>()
            val client = mirroredSubscriptionClient { request ->
                hosts += request.url.host
                val isRoot = hosts.size == 1
                respond(if (isRoot) "root" else "child", headers = headersOf(
                    "profile-title" to listOf(if (isRoot) "Root" else "Child"),
                    SubscriptionUsageHeader to listOf(if (isRoot) "total=10" else "total=99"),
                ))
            }
            try {
                val importer = SubscriptionImporter(mirrorSubscriptionFetcher(client), {
                    if (it == "root") providerSnapshot(testProvider("child", child)) else testSnapshot(testNode())
                })
                val result = importer.importSubscription(root, settings, mirrors = subscriptionMirrors)
                assertNull(result.failure)
                assertEquals("Root", result.name)
                assertEquals(SubscriptionUsage(total = 10), result.usage)
                assertEquals(if (rootIsGithub) listOf("b.example", "child.example") else listOf("sub.example", "b.example"), hosts)
            } finally { client.close() }
        }
    }

    @Test
    fun sensitiveProviderHeadersStayAtOriginWhileExplicitUaIsPreserved() = runTest {
        for (secretHeader in listOf("Authorization", "Cookie", "X-Api-Key", "X-Token")) {
            val hosts = mutableListOf<String>()
            val client = mirroredSubscriptionClient { request ->
                hosts += request.url.host
                if (hosts.size == 2) {
                    assertEquals("private", request.headers[secretHeader])
                    assertEquals("provider-agent", request.headers[HttpHeaders.UserAgent])
                }
                respond(if (hosts.size == 1) "root" else "child")
            }
            try {
                val provider = testProvider("child", mirrorSubscriptionUrl, extra = buildJsonObject {
                    put("header", buildJsonObject { put(secretHeader, "private"); put("User-Agent", "provider-agent") })
                })
                val importer = SubscriptionImporter(mirrorSubscriptionFetcher(client), {
                    if (it == "root") providerSnapshot(provider) else testSnapshot(testNode())
                })
                assertNull(importer.importSubscription("https://sub.example/root", settings, mirrors = subscriptionMirrors).failure)
                assertEquals(listOf("sub.example", "raw.githubusercontent.com"), hosts)
            } finally { client.close() }
        }
    }

    @Test
    fun mirrorRetriesAndRemainingCandidatesCannotExtendWholeImportBudget() = runTest {
        val hosts = mutableListOf<String>()
        val client = mirroredSubscriptionClient { request -> hosts += request.url.host; delay(4000); respond("late") }
        try {
            val importer = SubscriptionImporter(mirrorSubscriptionFetcher(client), { error("must not parse") }, Clock { testScheduler.currentTime })
            val result = importer.importSubscription(mirrorSubscriptionUrl, settings.copy(retries = 1), mirrors = subscriptionMirrors)
            assertEquals("请求超时", result.failure)
            assertEquals(listOf("b.example", "b.example", "a.example", "a.example"), hosts)
            assertEquals(12_000L, testScheduler.currentTime)
        } finally { client.close() }
    }

    @Test
    fun providerSharesRemainingRootBudgetRatherThanStartingANewBudget() = runTest {
        val hosts = mutableListOf<String>()
        val client = mirroredSubscriptionClient { request ->
            hosts += request.url.host
            if (request.url.host == "sub.example") { delay(2000); respond("root") }
            else { delay(4000); respond("late") }
        }
        try {
            val importer = SubscriptionImporter(mirrorSubscriptionFetcher(client), {
                providerSnapshot(testProvider("child", mirrorSubscriptionUrl)).copy(nodes = listOf(testNode()))
            }, Clock { testScheduler.currentTime })
            val result = importer.importSubscription("https://sub.example/root", settings.copy(retries = 1), mirrors = subscriptionMirrors)
            assertNull(result.failure)
            assertEquals(listOf("sub.example", "b.example", "b.example", "a.example", "a.example"), hosts)
            assertEquals(12_000L, testScheduler.currentTime)
        } finally { client.close() }
    }

    @Test
    fun mirrorRedirectsRejectThirdPartyDestinationsAndKeepDnsChecks() = runTest {
        val hosts = mutableListOf<String>()
        val verified = mutableListOf<String>()
        val client = mirroredSubscriptionClient { request ->
            hosts += request.url.host
            if (request.url.host == "b.example") respond("", HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "https://attacker.example/steal"))
            else respond("nodes")
        }
        try {
            val fetcher = SubscriptionFetcher(client, { client }, { verified += it })
            val importer = SubscriptionImporter(fetcher, { testSnapshot(testNode()) })
            assertNull(importer.importSubscription(mirrorSubscriptionUrl, settings, mirrors = subscriptionMirrors).failure)
            assertEquals(listOf("b.example", "a.example"), hosts)
            assertEquals(hosts, verified)
        } finally { client.close() }
    }
}
