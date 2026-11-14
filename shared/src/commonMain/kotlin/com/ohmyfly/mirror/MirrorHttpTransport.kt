// 网络日志只记录安全来源、状态、体积和计时。
package com.ohmyfly.mirror

import com.ohmyfly.logging.logger
import com.ohmyfly.logging.logTarget

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.request.prepareGet
import io.ktor.client.request.prepareHead
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/** One process-scoped client per platform, with streaming rather than automatic body caching. */
internal class MirrorHttpTransport(private val client: HttpClient) : MirrorTransport {
    override suspend fun head(url: String, timeoutMillis: Long): MirrorHeadResponse {
        logger.debug { "[镜像HTTP] HEAD ${logTarget(url)}，超时 ${timeoutMillis}ms" }
        parseHttpUrl(url) ?: throw MirrorFetchException("请求地址不是公网 HTTP 或 HTTPS 地址")
        require(timeoutMillis in 1..60_000)
        return withTimeoutOrNull(timeoutMillis) {
            val start = TimeSource.Monotonic.markNow()
            // 只计首个响应头；不跟随 3xx，也不消费正文。
            client.prepareHead(url) {
                headers.append(HttpHeaders.CacheControl, "no-cache")
                headers.append(HttpHeaders.UserAgent, "ohmyfly-mirror-probe")
            }.execute { response ->
                MirrorHeadResponse(response.status.value, start.elapsedNow().inWholeMilliseconds)
            }
        } ?: throw MirrorTimeoutException()
    }

    override suspend fun fetch(url: String, timeoutMillis: Long, maxBytes: Int): ProbeResponse {
        logger.debug { "[镜像HTTP] GET ${logTarget(url)}，超时 ${timeoutMillis}ms，最多 ${maxBytes}B" }
        parseHttpUrl(url) ?: throw MirrorFetchException("请求地址不是公网 HTTP 或 HTTPS 地址")
        require(timeoutMillis in 1..60_000 && maxBytes in 1..1_048_576)
        return withTimeoutOrNull(timeoutMillis) {
            val start = TimeSource.Monotonic.markNow()
            client.prepareGet(url) {
                headers.append(HttpHeaders.AcceptEncoding, "identity")
                headers.append(HttpHeaders.CacheControl, "no-cache")
                headers.append(HttpHeaders.UserAgent, "ohmyfly-mirror-probe")
            }.execute { response ->
                val status = response.status.value
                logger.debug { "[镜像HTTP] ${logTarget(url)} 响应 HTTP $status，响应头耗时 ${start.elapsedNow().inWholeMilliseconds}ms" }
                if (status !in 200..299) {
                    return@execute ProbeResponse(status, byteArrayOf(), start.elapsedNow().inWholeMilliseconds,
                        response.headers[HttpHeaders.Location])
                }
                val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (declared != null && declared > maxBytes) throw MirrorFetchException("响应超过大小上限")
                val channel = response.bodyAsChannel()
                val bytes = ByteArray(maxBytes)
                val chunk = ByteArray(minOf(4096, maxBytes + 1))
                var count = 0
                var firstByte: Long? = null
                while (true) {
                    val read = channel.readAvailable(chunk, 0, minOf(chunk.size, maxBytes - count + 1))
                    if (read < 0) break
                    if (read == 0) continue
                    if (firstByte == null) firstByte = start.elapsedNow().inWholeMilliseconds
                    if (count + read > maxBytes) throw MirrorFetchException("响应超过大小上限")
                    chunk.copyInto(bytes, count, 0, read)
                    count += read
                }
                logger.debug { "[镜像HTTP] ${logTarget(url)} 读取完成：服务器${declared?.let { "声明 $it 字节" } ?: "未声明内容长度"}，实际读取 ${count} 字节，${firstByte?.let { "等待首字节 $it 毫秒" } ?: "没有收到正文"}，总耗时 ${start.elapsedNow().inWholeMilliseconds} 毫秒" }
                ProbeResponse(status, bytes.copyOf(count), firstByte ?: start.elapsedNow().inWholeMilliseconds)
            }
        } ?: throw MirrorTimeoutException()
    }
}

internal fun HttpClientConfig<*>.mirrorHttpPolicy() {
    followRedirects = false
    expectSuccess = false
    // No logging, Auth, HttpCookies, or body transformation plugins are installed.
}
