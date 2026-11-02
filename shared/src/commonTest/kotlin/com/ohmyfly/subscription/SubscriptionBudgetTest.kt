package com.ohmyfly.subscription

import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class SubscriptionBudgetTest {
    private class Clock(private val now: () -> Long) : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
        override fun read(): Long = now()
    }

    @Test
    fun timeoutRetriesAndLaterAgentsShareTheSameFourTimeoutBudget() = runTest {
        val agents = mutableListOf<String?>()
        val client = subscriptionClient { request ->
            agents += request.headers[HttpHeaders.UserAgent]
            delay(4000)
            respondOk("late")
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { error("must not parse") }, Clock { testScheduler.currentTime })
            val result = importer.importSubscription("https://sub.example/p", SubscriptionSettings(timeoutSeconds = 3, retries = 1))
            assertEquals("请求超时", result.failure)
            assertEquals(listOf("v2rayN/7.20.4", "v2rayN/7.20.4", "mihomo/1.19.24", "mihomo/1.19.24"), agents)
            assertEquals(12000L, testScheduler.currentTime)
        } finally { client.close() }
    }

    @Test
    fun manyRetriesCannotExtendTheTotalBudget() = runTest {
        var requests = 0
        val client = subscriptionClient { requests++; delay(4000); respondOk("late") }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { error("must not parse") }, Clock { testScheduler.currentTime })
            val result = importer.importSubscription("https://sub.example/p", SubscriptionSettings(timeoutSeconds = 3, retries = 5, userAgent = "a|b"))
            assertEquals("请求超时", result.failure)
            assertEquals(4, requests)
            assertEquals(12000L, testScheduler.currentTime)
        } finally { client.close() }
    }

    @Test
    fun sixtySecondCapShrinksTheNextAttemptAndKeepsEarlierSuccess() = runTest {
        var parsingMillis = 0L
        var requests = 0
        val client = subscriptionClient {
            requests++
            if (requests > 1) delay(20000)
            respondOk("p")
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), {
                parsingMillis += 45000
                testSnapshot(testNode("first"))
            }, Clock { parsingMillis + testScheduler.currentTime })
            val result = importer.importSubscription("https://sub.example/p", SubscriptionSettings(timeoutSeconds = 30, userAgent = "a|b|c"))
            assertNull(result.failure)
            assertEquals(listOf("first"), result.nodes.map { it.name })
            assertEquals(2, requests)
            assertEquals(15000L, testScheduler.currentTime)
        } finally { client.close() }
    }

    @Test
    fun timeoutUsesConfiguredValueAndCanRecoverOnSameAgent() = runTest {
        var requests = 0
        val client = subscriptionClient {
            requests++
            if (requests == 1) delay(4000)
            respondOk("p")
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) }, Clock { testScheduler.currentTime })
            val result = importer.importSubscription("https://sub.example/p", SubscriptionSettings(timeoutSeconds = 3, retries = 1, userAgent = "a"))
            assertNull(result.failure)
            assertEquals(2, requests)
            assertEquals(3000L, testScheduler.currentTime)
        } finally { client.close() }
    }

    @Test
    fun parsingTimeAlsoConsumesTheNegotiationBudget() = runTest {
        var elapsed = 0L
        var requests = 0
        val client = subscriptionClient { requests++; respondOk("p") }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), {
                elapsed += 12000
                testSnapshot(testNode())
            }, Clock { elapsed })
            assertNull(importer.importSubscription("https://sub.example/p", SubscriptionSettings(timeoutSeconds = 3)).failure)
            assertEquals(1, requests)
        } finally { client.close() }
    }
}
