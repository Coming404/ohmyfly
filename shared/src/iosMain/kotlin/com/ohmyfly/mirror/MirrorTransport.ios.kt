// 镜像解析与连接统一交给 NSURLSession，保留系统 VPN 与代理路由。
package com.ohmyfly.mirror

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

private val client: HttpClient by lazy {
    HttpClient(Darwin) {
        mirrorHttpPolicy()
        engine {
            configureSession {
                HTTPShouldSetCookies = false
                HTTPCookieStorage = null
                URLCredentialStorage = null
                URLCache = null
            }
        }
    }
}

private val transport: MirrorTransport by lazy { MirrorHttpTransport(client) }
internal actual fun createMirrorClient(): HttpClient = client
internal actual fun createMirrorTransport(): MirrorTransport = transport
