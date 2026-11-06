@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ohmyfly.network

import com.ohmyfly.settings.SpeedSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.head
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** An actual connection lets iOS present its network-access prompt when applicable. */
internal suspend fun requestStartupNetworkAccess() {
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
        val target = SpeedSettings().latencyUrl
        com.ohmyfly.logging.logger.debug { "[启动联网] HEAD ${com.ohmyfly.logging.logTarget(target)}，超时 10000ms" }
        val response = withTimeoutOrNull(10_000L) { client.head(target) }
        com.ohmyfly.logging.logger.info { "[启动联网] ${response?.let { "HTTP ${it.status.value}" } ?: "请求超时"}" }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        com.ohmyfly.logging.logger.info { "[启动联网] 请求失败，异常类型=${error::class.simpleName}，继续应用启动" }
        // 请求结果不等于系统权限状态，不因此阻断启动。
    } finally {
        client.close()
    }
}
