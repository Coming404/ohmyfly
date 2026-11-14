// 镜像请求沿用系统 DNS、代理与 VPN；URL 边界在共享请求层验证。
package com.ohmyfly.mirror

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Authenticator
import okhttp3.CookieJar

private val client: HttpClient by lazy {
    HttpClient(OkHttp) {
        mirrorHttpPolicy()
        engine {
            config {
                followRedirects(false)
                followSslRedirects(false)
                retryOnConnectionFailure(false)
                authenticator(Authenticator.NONE)
                proxyAuthenticator(Authenticator.NONE)
                cookieJar(CookieJar.NO_COOKIES)
                cache(null)
                // 不强制直连或过滤解析结果，Fake-IP 由系统 VPN 接管。
            }
        }
    }
}

private val transport: MirrorTransport by lazy { MirrorHttpTransport(client) }
internal actual fun createMirrorClient(): HttpClient = client
internal actual fun createMirrorTransport(): MirrorTransport = transport
