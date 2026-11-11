// Ported from SubBoost local/src/lib/{source-import,user-agents}.ts @401534742802e2b1542783197ce66451bad82739 (AGPL-3.0-only).
package com.ohmyfly.subscription

import com.ohmyfly.logging.logger
import com.ohmyfly.logging.logTarget
import kotlin.time.TimeSource

import com.ohmyfly.mirror.isGitHubHost
import com.ohmyfly.mirror.createMirrorClient
import com.ohmyfly.mirror.ensurePublicHost
import com.ohmyfly.mirror.parseHttpUrl
import com.ohmyfly.mirror.resolveRedirect
import com.ohmyfly.mirror.MAX_REDIRECTS
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
private class SubscriptionRedirect(val location: String) : Exception()

/** 一个平台一个进程级客户端；跟随重定向，不安装日志、Cookie 等插件。 */
internal expect fun createSubscriptionClient(): HttpClient

// GitHub 静态订阅只使用浏览器 UA；普通订阅仍按客户端 UA 择优。
internal const val MozillaSubscriptionUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36"

internal const val DefaultSubscriptionUserAgents =
    "v2rayN/7.20.4|mihomo/1.19.24|clash-verge/v2.5.2|" + MozillaSubscriptionUserAgent

internal val BuiltInSubscriptionUserAgents = DefaultSubscriptionUserAgents.split('|').distinct().take(4)

internal fun subscriptionUserAgents(url: String, custom: String): List<String?> {
    val host = parseUrl(url)?.host
    if (host != null && isGitHubHost(host)) {
        return listOf(MozillaSubscriptionUserAgent)
    }
    val agents = custom.split('|').map(String::trim).filter(String::isNotEmpty).distinct().take(4)
    return agents.ifEmpty { BuiltInSubscriptionUserAgents }
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

internal data class SubscriptionResponse(
    val body: String, val usage: SubscriptionUsage?, val name: String? = null,
)

internal class SubscriptionFetcher(
    private val client: HttpClient,
    private val mirrorClient: () -> HttpClient = ::createMirrorClient,
    private val verifyHost: suspend (String) -> Unit = ::ensurePublicHost,
) {
    suspend fun fetch(
        url: String, settings: SubscriptionSettings, userAgent: String?, timeoutMillis: Long,
        requestHeaders: Map<String, List<String>> = emptyMap(),
        operation: String = "",
        resolveName: Boolean = true,
        publicMirror: Boolean = false,
    ): SubscriptionResponse {
        if (!publicMirror) return request(client, url, settings, userAgent, timeoutMillis, requestHeaders, operation, resolveName)
        return withTimeoutOrNull(timeoutMillis) {
            var current = url
            repeat(MAX_REDIRECTS + 1) { hop ->
                val parsed = parseHttpUrl(current) ?: throw SubscriptionFetchException("镜像地址无效", false)
                verifyHost(parsed.host)
                try {
                    return@withTimeoutOrNull request(mirrorClient(), current, settings, userAgent,
                        timeoutMillis, requestHeaders, operation, resolveName, redirect = true)
                } catch (next: SubscriptionRedirect) {
                    if (hop == MAX_REDIRECTS) throw SubscriptionFetchException("重定向次数超过上限", false)
                    current = resolveRedirect(current, next.location)
                        ?: throw SubscriptionFetchException("重定向目标不被允许", false)
                }
            }
            throw SubscriptionFetchException("重定向失败", false)
        } ?: throw SubscriptionFetchException("请求超时", true)
    }

    private suspend fun request(
        requestClient: HttpClient, url: String, settings: SubscriptionSettings,
        userAgent: String?, timeoutMillis: Long, requestHeaders: Map<String, List<String>>,
        operation: String, resolveName: Boolean, redirect: Boolean = false,
    ): SubscriptionResponse {
        val started = TimeSource.Monotonic.markNow()
        val maxBytes = settings.maxMiB.toLong() * 1024 * 1024
        val actualAgents = requestHeaders.entries.firstOrNull { it.key.equals(HttpHeaders.UserAgent, ignoreCase = true) }
            ?.value ?: listOf(userAgent)
        logger.debug { "[订阅 $operation] 向 ${logTarget(url)} 发送 GET 请求，最多等待 ${timeoutMillis} 毫秒，最多下载 ${maxBytes} 字节，附加 ${requestHeaders.size} 个请求头；实际使用：${actualAgents.joinToString("、", transform = ::subscriptionUserAgentDescription)}" }
        return withTimeoutOrNull(timeoutMillis) {
            requestClient.prepareGet(url) {
                headers.append(HttpHeaders.CacheControl, "no-cache")
                if (userAgent != null) headers.append(HttpHeaders.UserAgent, userAgent)
                requestHeaders.forEach { (name, values) ->
                    headers.remove(name)
                    values.forEach { headers.append(name, it) }
                }
            }.execute { response ->
                val status = response.status.value
                logger.debug { "[订阅 $operation] 响应 HTTP $status，最终目标 ${logTarget(response.call.request.url.toString())}，耗时 ${started.elapsedNow().inWholeMilliseconds}ms" }
                if (redirect && status in setOf(301, 302, 303, 307, 308)) {
                    val location = response.headers[HttpHeaders.Location]
                        ?: throw SubscriptionFetchException("重定向目标不被允许", false)
                    throw SubscriptionRedirect(location)
                }
                if (status !in 200..299) {
                    throw SubscriptionFetchException("HTTP $status", retryable = isRetryableStatus(status))
                }
                val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (declared != null && declared > maxBytes) throw SubscriptionFetchException("订阅超过大小上限", false)
                val bytes = response.bodyAsChannel().readRemaining(maxBytes + 1).readByteArray()
                if (bytes.size > maxBytes) throw SubscriptionFetchException("订阅超过大小上限", false)
                logger.debug { "[订阅 $operation] 下载完成：服务器${declared?.let { "声明 $it 字节" } ?: "未声明内容长度"}，实际下载 ${bytes.size} 字节，总耗时 ${started.elapsedNow().inWholeMilliseconds} 毫秒" }
                SubscriptionResponse(
                    bytes.decodeToString(), parseSubscriptionUserInfo(response.headers[SubscriptionUsageHeader]),
                    if (resolveName) parseSubscriptionName(
                        response.headers["profile-title"], response.headers[HttpHeaders.ContentDisposition],
                    ) else null,
                )
            }
        } ?: throw SubscriptionFetchException("请求超时", retryable = true)
    }
}
