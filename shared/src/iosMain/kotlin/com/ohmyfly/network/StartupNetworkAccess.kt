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
        withTimeoutOrNull(10_000L) {
            client.head(SpeedSettings().latencyUrl)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Offline, denied access and endpoint failure must not prevent startup.
        // A response or failure is not a reliable permission-state API.
    } finally {
        client.close()
    }
}
