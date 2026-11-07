@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ohmyfly.network

import com.ohmyfly.logging.logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.head
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSUserDefaults

private const val StartupNetworkAttemptedKey = "startupNetworkAccessAttempted"

/** 首次尝试建立连接，让 iOS 在需要时显示联网授权提示。 */
internal suspend fun requestStartupNetworkAccess() {
    val defaults = NSUserDefaults.standardUserDefaults
    if (defaults.boolForKey(StartupNetworkAttemptedKey)) return
    // 请求前保存，失败或取消后也不在后续启动重复请求。
    defaults.setBool(true, forKey = StartupNetworkAttemptedKey)
    val client = HttpClient(Darwin) {
        followRedirects = false
        engine {
            configureSession {
                HTTPShouldSetCookies = false
                HTTPCookieStorage = null
                URLCredentialStorage = null
                URLCache = null
                waitsForConnectivity = false
            }
        }
    }
    try {
        val target = "https://www.bing.com"
        logger.debug { "[启动联网] 首次尝试向 $target 发送 HEAD 请求，最多等待 10 秒" }
        val response = withTimeoutOrNull(10_000L) { client.head(target) }
        logger.info { "[启动联网] ${response?.let { "首次请求已收到响应，HTTP 状态码为 ${it.status.value}" } ?: "首次请求等待超过 10 秒"}，后续启动不再重复" }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        logger.info { "[启动联网] 首次请求失败，异常类型为 ${error::class.simpleName}；继续启动应用，后续启动不再重复" }
        // 请求结果不等于系统权限状态，不因此阻断启动。
    } finally {
        client.close()
    }
}
