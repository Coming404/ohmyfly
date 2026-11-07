// 通过实际导入链路核对 UA、跳过原因和重试说明，防止凭据进入日志。
package com.ohmyfly.subscription

import com.ohmyfly.logging.AppLog
import io.github.oshai.kotlinlogging.Appender
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionLoggingTest {
    @Test
    fun requestsExposeBuiltInAgentAndKeepProtocolSpecificSkipReasons() = runTest {
        val logs = mutableListOf<String>()
        val saved = captureLogs(logs)
        val client = subscriptionClient { respondOk("profile") }
        try {
            val snapshot = testSnapshot(testNode(), skipped = 3).copy(
                skippedByReason = mapOf("malformed link" to 1, "invalid vmess link" to 1, "unsupported protocol: ssh" to 1),
            )
            SubscriptionImporter(SubscriptionFetcher(client), { snapshot }).importSubscription(
                "https://sub.example/secret?token=hidden", SubscriptionSettings(),
            )
            val text = logs.joinToString("\n")
            BuiltInSubscriptionUserAgents.forEach { assertTrue(text.contains("内置 UA「$it」")) }
            assertTrue(text.contains("缺少合法的协议前缀"))
            assertTrue(text.contains("vmess 链接字段无效"))
            assertTrue(text.contains("不支持 ssh 协议"))
            assertFalse(text.contains("malformed link"))
            assertFalse(text.contains("/secret"))
            assertFalse(text.contains("token=hidden"))
        } finally {
            client.close()
            restoreLogs(saved)
        }
    }

    @Test
    fun effectiveCustomHeaderIsIdentifiableWithoutItsOriginalValue() = runTest {
        val logs = mutableListOf<String>()
        val saved = captureLogs(logs)
        val custom = "private-agent-secret"
        val client = subscriptionClient { request ->
            assertEquals(listOf(custom), request.headers.getAll(HttpHeaders.UserAgent))
            respondOk("profile")
        }
        try {
            SubscriptionFetcher(client).fetch(
                "https://sub.example/p", SubscriptionSettings(), BuiltInSubscriptionUserAgents.first(), 1000,
                mapOf(HttpHeaders.UserAgent to listOf(custom)), "test",
            )
            val text = logs.joinToString("\n")
            assertTrue(text.contains(subscriptionUserAgentDescription(custom)))
            assertTrue(text.contains("SHA-256 前 12 位"))
            assertFalse(text.contains(custom))
            assertFalse(text.contains("内置 UA"))
            assertFalse(subscriptionUserAgentDescription(custom) == subscriptionUserAgentDescription("other-agent-secret"))
        } finally {
            client.close()
            restoreLogs(saved)
        }
    }

    @Test
    fun retryLogsExplainHttpFailureAndWhyTheRetryStops() = runTest {
        val logs = mutableListOf<String>()
        val saved = captureLogs(logs)
        var requests = 0
        val client = subscriptionClient { requests++; respond("private-body", HttpStatusCode.ServiceUnavailable) }
        try {
            SubscriptionImporter(SubscriptionFetcher(client), { testSnapshot() }).importSubscription(
                "https://sub.example/p", SubscriptionSettings(userAgent = "custom", retries = 1),
            )
            val text = logs.joinToString("\n")
            assertEquals(2, requests)
            assertTrue(text.contains("服务器暂时无法提供服务（HTTP 503）"))
            assertTrue(text.contains("第 1 次重试"))
            assertTrue(text.contains("已用完 1 次重试机会"))
            assertFalse(text.contains("private-body"))
        } finally {
            client.close()
            restoreLogs(saved)
        }
    }
}

private fun captureLogs(messages: MutableList<String>): Pair<Appender, Level> {
    AppLog.logger
    val saved = KotlinLoggingConfiguration.direct.appender to KotlinLoggingConfiguration.direct.logLevel
    KotlinLoggingConfiguration.direct.logLevel = Level.DEBUG
    KotlinLoggingConfiguration.direct.appender = object : Appender {
        override fun log(loggingEvent: KLoggingEvent) { messages += loggingEvent.message.orEmpty() }
    }
    return saved
}

private fun restoreLogs(saved: Pair<Appender, Level>) {
    KotlinLoggingConfiguration.direct.appender = saved.first
    KotlinLoggingConfiguration.direct.logLevel = saved.second
}
