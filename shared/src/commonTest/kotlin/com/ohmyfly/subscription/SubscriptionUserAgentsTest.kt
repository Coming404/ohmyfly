package com.ohmyfly.subscription

import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionUserAgentsTest {
    @Test
    fun allBuiltInProfilesAreFetchedAndParsedInOrder() = runTest {
        val agents = mutableListOf<String?>()
        var parsed = 0
        val client = subscriptionClient { request ->
            agents += request.headers[HttpHeaders.UserAgent]
            assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
            respondOk("profile")
        }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { parsed++; testSnapshot(testNode()) })
            assertNull(importer.importSubscription("https://sub.example/p", SubscriptionSettings()).failure)
            assertEquals(listOf(
                "v2rayN/7.20.4", "mihomo/1.19.24", "clash-verge/v2.5.2",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            ), agents)
            assertEquals(4, parsed)
        } finally { client.close() }
    }

    @Test
    fun oneCustomAgentKeepsCommasAndSemicolonsAndMakesOneRequest() = runTest {
        val agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
        val agents = mutableListOf<String?>()
        val client = subscriptionClient { request -> agents += request.headers[HttpHeaders.UserAgent]; respondOk("p") }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) })
            importer.importSubscription("https://sub.example/p", SubscriptionSettings(userAgent = " $agent "))
            assertEquals(listOf(agent), agents)
        } finally { client.close() }
    }

    @Test
    fun customListIsTrimmedDeduplicatedAndLimitedToFourProfiles() = runTest {
        val agents = mutableListOf<String?>()
        val client = subscriptionClient { request -> agents += request.headers[HttpHeaders.UserAgent]; respondOk("p") }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) })
            importer.importSubscription("https://sub.example/p", SubscriptionSettings(userAgent = " | a | b || a | c | d | e |"))
            assertEquals(listOf("a", "b", "c", "d"), agents)
        } finally { client.close() }
    }

    @Test
    fun githubEmptySettingsSendNoAgentAndOnlyOneRequest() = runTest {
        for (setting in listOf("", " | ")) {
            var requests = 0
            val client = subscriptionClient { request ->
                requests++
                assertNull(request.headers[HttpHeaders.UserAgent])
                respondOk("p")
            }
            try {
                val importer = SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) })
                assertNull(importer.importSubscription(
                    "https://RAW.GITHUBUSERCONTENT.COM./p", SubscriptionSettings(userAgent = setting),
                ).failure)
                assertEquals(1, requests)
            } finally { client.close() }
        }
    }

    @Test
    fun githubUsesChangedCustomListInOrder() = runTest {
        val agents = mutableListOf<String?>()
        val client = subscriptionClient { request -> agents += request.headers[HttpHeaders.UserAgent]; respondOk("p") }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) })
            importer.importSubscription("https://raw.githubusercontent.com/p", SubscriptionSettings(userAgent = " a | b "))
            assertEquals(listOf("a", "b"), agents)
        } finally { client.close() }
    }

    @Test
    fun githubLookalikeHostStillNegotiatesAllProfiles() = runTest {
        var requests = 0
        val client = subscriptionClient { requests++; respondOk("p") }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) })
            importer.importSubscription("https://raw.githubusercontent.com.evil.example/p", SubscriptionSettings())
            assertEquals(4, requests)
        } finally { client.close() }
    }

    @Test
    fun listWithoutAnyAgentFallsBackToBuiltIns() = runTest {
        var requests = 0
        val client = subscriptionClient { requests++; respondOk("p") }
        try {
            val importer = SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot(testNode()) })
            importer.importSubscription("https://sub.example/p", SubscriptionSettings(userAgent = " ||  | "))
            assertEquals(4, requests)
        } finally { client.close() }
    }
}
