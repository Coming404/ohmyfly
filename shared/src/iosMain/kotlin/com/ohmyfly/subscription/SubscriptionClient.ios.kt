package com.ohmyfly.subscription

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

private val client: HttpClient by lazy {
    HttpClient(Darwin) {
        engine {
            configureSession {
                HTTPShouldSetCookies = false
                HTTPCookieStorage = null
                URLCache = null
            }
        }
    }
}

internal actual fun createSubscriptionClient(): HttpClient = client
