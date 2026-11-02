package com.ohmyfly.subscription

import com.ohmyfly.settings.SubscriptionSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionFiltersTest {
    @Test
    fun includeRegexIsTrimmedCaseInsensitiveAndMatchesPartOfName() = runTest {
        val result = importSnapshots(
            testSnapshot(testNode("prefix-HK-01", server = "hk.example"), testNode("US", server = "us.example")),
            settings = SubscriptionSettings(includePattern = " hk "),
        )
        assertEquals(listOf("prefix-HK-01"), result.nodes.map { it.name })
    }

    @Test
    fun excludeRegexSupportsAlternationAndIgnoresCase() = runTest {
        val result = importSnapshots(
            testSnapshot(testNode("剩余流量：10 GB"), testNode("套餐到期：明天"), testNode("hk-expired-01"), testNode("HK usable")),
            settings = SubscriptionSettings(excludePattern = " 到期|流量|EXPIRED "),
        )
        assertEquals(listOf("HK usable"), result.nodes.map { it.name })
    }

    @Test
    fun emptyAndWhitespacePatternsDoNotRestrictNodes() = runTest {
        for (pattern in listOf("", " \t\n ")) {
            val result = importSnapshots(
                testSnapshot(testNode("HK", server = "hk.example"), testNode("US", server = "us.example")),
                settings = SubscriptionSettings(includePattern = pattern, excludePattern = pattern),
            )
            assertNull(result.failure)
            assertEquals(listOf("HK", "US"), result.nodes.map { it.name })
        }
    }

    @Test
    fun protocolSetOnlyKeepsTheRequestedTypes() = runTest {
        val result = importSnapshots(
            testSnapshot(testNode(type = "ss"), testNode(type = "vmess"), testNode(type = "trojan")),
            settings = SubscriptionSettings(protocols = setOf("vmess", "trojan")),
        )
        assertEquals(listOf("vmess", "trojan"), result.nodes.map { it.type })
    }

    @Test
    fun includeExcludeAndProtocolRestrictionsApplyTogether() = runTest {
        val result = importSnapshots(
            testSnapshot(
                testNode("HK SS", server = "ss.example"), testNode("HK slow", type = "trojan", server = "slow.example"),
                testNode("HK fast", type = "trojan", server = "fast.example"), testNode("US fast", type = "trojan", server = "us.example"),
            ),
            settings = SubscriptionSettings(includePattern = "HK", excludePattern = "slow", protocols = setOf("trojan")),
        )
        assertEquals(listOf("HK fast"), result.nodes.map { it.name })
    }

    @Test
    fun invalidIncludeRegexReturnsAnExplicitChineseError() = runTest {
        val result = importSnapshots(testSnapshot(testNode()), settings = SubscriptionSettings(includePattern = "["))
        assertEquals("包含规则不是有效的正则表达式", result.failure)
        assertEquals(emptyList(), result.nodes)
    }

    @Test
    fun invalidExcludeRegexReturnsAnExplicitChineseError() = runTest {
        val result = importSnapshots(testSnapshot(testNode()), settings = SubscriptionSettings(excludePattern = "("))
        assertEquals("排除规则不是有效的正则表达式", result.failure)
    }

    @Test
    fun patternsOver512CharactersAreRejected() = runTest {
        val tooLong = "a".repeat(513)
        for (settings in listOf(SubscriptionSettings(includePattern = tooLong), SubscriptionSettings(excludePattern = tooLong))) {
            assertEquals("塔台过滤规则不能超过 512 个字符", importSnapshots(testSnapshot(testNode()), settings = settings).failure)
        }
    }

    @Test
    fun exactly512CharactersAfterTrimmingAreAccepted() = runTest {
        val pattern = "a".repeat(512)
        val result = importSnapshots(testSnapshot(testNode(pattern)), settings = SubscriptionSettings(includePattern = " $pattern "))
        assertNull(result.failure)
        assertEquals(listOf(pattern), result.nodes.map { it.name })
    }

    @Test
    fun filtersRunAfterSelectionWithoutFallingBackToASmallerProfile() = runTest {
        val result = importSnapshots(
            testSnapshot(testNode("HK", server = "hk.example")),
            testSnapshot(testNode("US1", server = "us1.example"), testNode("US2", server = "us2.example")),
            settings = SubscriptionSettings(includePattern = "HK"),
        )
        assertNull(result.failure)
        assertEquals(emptyList(), result.nodes)
    }
}
