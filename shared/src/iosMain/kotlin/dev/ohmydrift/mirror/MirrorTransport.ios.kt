@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.ohmydrift.mirror

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.cinterop.*
import platform.posix.*

private val transport: MirrorTransport by lazy {
    MirrorHttpTransport(HttpClient(Darwin) {
        mirrorHttpPolicy()
        engine {
            configureSession {
                HTTPShouldSetCookies = false
                HTTPCookieStorage = null
                URLCredentialStorage = null
                URLCache = null
                connectionProxyDictionary = emptyMap<Any?, Any?>()
            }
        }
    })
}

internal actual fun createMirrorTransport(): MirrorTransport = transport

/**
 * Rejects a resolver response containing any non-public address.
 * shortcut: NSURLSession resolves again internally, so preflight cannot fully pin DNS against
 * rebinding; use only user-approved public mirrors with no credentials, never private resources.
 */
internal actual suspend fun ensurePublicHost(host: String) {
    withContext(Dispatchers.Default) {
        memScoped {
            val hints = alloc<addrinfo> {
                ai_flags = 0
                ai_family = AF_UNSPEC
                ai_socktype = SOCK_STREAM
                ai_protocol = IPPROTO_TCP
                ai_addrlen = 0u
                ai_addr = null
                ai_canonname = null
                ai_next = null
            }
            val result = alloc<CPointerVar<addrinfo>>()
            result.value = null
            if (getaddrinfo(host, null, hints.ptr, result.ptr) != 0) throw MirrorFetchException("无法解析镜像地址")
            try {
                var item = result.value
                var found = false
                while (item != null) {
                    val info = item.pointed
                    val address = info.ai_addr ?: throw MirrorFetchException("无效 DNS 响应")
                    val bytes = when (info.ai_family) {
                        AF_INET -> {
                            val raw = address.reinterpret<sockaddr_in>().pointed.sin_addr.ptr.reinterpret<ByteVar>()
                            ByteArray(4) { raw[it] }
                        }
                        AF_INET6 -> {
                            val raw = address.reinterpret<sockaddr_in6>().pointed.sin6_addr.ptr.reinterpret<ByteVar>()
                            ByteArray(16) { raw[it] }
                        }
                        else -> throw MirrorFetchException("不支持的地址类型")
                    }
                    if (!isPublicAddress(bytes)) throw MirrorFetchException("镜像不允许访问本地或保留地址")
                    found = true
                    item = info.ai_next
                }
                if (!found) throw MirrorFetchException("镜像没有可用公网地址")
            } finally {
                freeaddrinfo(result.value)
            }
        }
    }
}
