package dev.ohmydrift.mirror

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/** One process-scoped client per platform, with streaming rather than automatic body caching. */
internal class MirrorHttpTransport(
    private val client: HttpClient,
    private val verifyHost: suspend (String) -> Unit = ::ensurePublicHost,
) : MirrorTransport {
    override suspend fun fetch(url: String, timeoutMillis: Long, maxBytes: Int): ProbeResponse {
        val parsed = parseHttpUrl(url) ?: throw MirrorFetchException("请求地址不是公网 HTTP 或 HTTPS 地址")
        require(timeoutMillis in 1..60_000 && maxBytes in 1..1_048_576)
        return withTimeoutOrNull(timeoutMillis) {
            val start = TimeSource.Monotonic.markNow()
            verifyHost(parsed.host)
            client.prepareGet(url) {
                headers.append(HttpHeaders.AcceptEncoding, "identity")
                headers.append(HttpHeaders.CacheControl, "no-cache")
                headers.append(HttpHeaders.UserAgent, "ohmydrift-mirror-probe")
            }.execute { response ->
                val status = response.status.value
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

internal expect suspend fun ensurePublicHost(host: String)
