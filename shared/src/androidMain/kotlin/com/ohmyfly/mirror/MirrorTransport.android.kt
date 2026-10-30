package com.ohmyfly.mirror

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.Dns

private val transport: MirrorTransport by lazy {
    MirrorHttpTransport(HttpClient(OkHttp) {
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
                proxy(Proxy.NO_PROXY)
                // The checked addresses are the very addresses OkHttp connects to.
                dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> = publicAddresses(hostname)
                })
            }
        }
    })
}

internal actual fun createMirrorTransport(): MirrorTransport = transport

internal actual suspend fun ensurePublicHost(host: String) {
    withContext(Dispatchers.IO) { publicAddresses(host) }
}

private fun publicAddresses(host: String): List<InetAddress> {
    val addresses = InetAddress.getAllByName(host).toList()
    if (addresses.isEmpty() || addresses.any { !isPublicAddress(it.address) }) {
        throw UnknownHostException("镜像不允许访问本地或保留地址")
    }
    return addresses
}
