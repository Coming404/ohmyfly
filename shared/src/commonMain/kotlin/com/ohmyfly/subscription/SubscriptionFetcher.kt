// Ported from SubBoost local/src/lib/{source-import,user-agents}.ts @401534742802e2b1542783197ce66451bad82739 (AGPL-3.0-only).
package com.ohmyfly.subscription

import com.ohmyfly.settings.SubscriptionSettings
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.parseUrl
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray

internal class SubscriptionFetchException(message: String, val retryable: Boolean) : Exception(message)

/** 一个平台一个进程级客户端；跟随重定向，不安装日志、Cookie 等插件。 */
internal expect fun createSubscriptionClient(): HttpClient

internal const val DefaultSubscriptionUserAgents =
    "v2rayN/7.20.4|mihomo/1.19.24|clash-verge/v2.5.2|" +
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36"

internal val BuiltInSubscriptionUserAgents = DefaultSubscriptionUserAgents.split('|').distinct().take(4)

internal fun subscriptionUserAgents(url: String, custom: String): List<String?> {
    val agents = custom.split('|').map(String::trim).filter(String::isNotEmpty).distinct().take(4)
    if (agents.isNotEmpty()) return agents
    val host = parseUrl(url)?.host?.lowercase()?.trimEnd('.')
    if (host?.endsWith(".githubusercontent.com") == true) return listOf(null)
    return BuiltInSubscriptionUserAgents
}

/** 返回错误信息；地址可用时返回 null。 */
internal fun validateSubscriptionUrl(text: String): String? {
    val url = parseUrl(text.trim()) ?: return "地址格式无效"
    if (url.protocol.name != "http" && url.protocol.name != "https") return "只支持 HTTP 或 HTTPS 地址"
    if (url.host.isEmpty()) return "地址缺少主机名"
    return null
}

private fun isRetryableStatus(status: Int): Boolean =
    status >= 500 || status == 429 || status == 401 || status == 403

internal class SubscriptionFetcher(private val client: HttpClient) {
    suspend fun fetch(
        url: String, settings: SubscriptionSettings, userAgent: String?, timeoutMillis: Long,
        requestHeaders: Map<String, List<String>> = emptyMap(),
    ): String {
        val maxBytes = settings.maxMiB.toLong() * 1024 * 1024
        return withTimeoutOrNull(timeoutMillis) {
            client.prepareGet(url) {
                headers.append(HttpHeaders.CacheControl, "no-cache")
                if (userAgent != null) headers.append(HttpHeaders.UserAgent, userAgent)
                requestHeaders.forEach { (name, values) ->
                    headers.remove(name)
                    values.forEach { headers.append(name, it) }
                }
            }.execute { response ->
                val status = response.status.value
                if (status !in 200..299) {
                    throw SubscriptionFetchException("HTTP $status", retryable = isRetryableStatus(status))
                }
                val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (declared != null && declared > maxBytes) throw SubscriptionFetchException("订阅超过大小上限", false)
                val bytes = response.bodyAsChannel().readRemaining(maxBytes + 1).readByteArray()
                if (bytes.size > maxBytes) throw SubscriptionFetchException("订阅超过大小上限", false)
                bytes.decodeToString()
            }
        } ?: throw SubscriptionFetchException("请求超时", retryable = true)
    }
}
