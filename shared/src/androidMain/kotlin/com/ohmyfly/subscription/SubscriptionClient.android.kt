package com.ohmyfly.subscription

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CookieJar

private val client: HttpClient by lazy {
    HttpClient(OkHttp) {
        engine {
            config {
                cookieJar(CookieJar.NO_COOKIES)
                cache(null)
            }
        }
    }
}

internal actual fun createSubscriptionClient(): HttpClient = client
