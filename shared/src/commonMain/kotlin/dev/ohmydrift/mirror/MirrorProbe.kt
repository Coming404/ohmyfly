package dev.ohmydrift.mirror

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString.Companion.toByteString

/**
 * Measures the same fixed resource through each mirror: three samples, TTFB median over samples
 * whose bounded body passed the pinned SHA-256, plus the success count. A non-2xx status or an
 * oversized body is never a success, and caller cancellation is rethrown rather than reported as
 * a mirror failure.
 */
internal class MirrorProbe(private val transport: MirrorTransport) {

    suspend fun measure(entry: MirrorEntry?): MirrorProbeResult {
        val target = entry?.let {
            validateMirror(it)?.let { error -> return failed(error) }
            buildMirrorUrl(it, MirrorProbeTarget.url)
        } ?: MirrorProbeTarget.url

        val latencies = mutableListOf<Long>()
        var timeouts = 0
        var badContent = 0
        var failures = 0
        repeat(SAMPLES) {
            when (val sample = attempt(target)) {
                is Sample.Ok -> latencies += sample.ttfbMs
                Sample.Timeout -> timeouts += 1
                Sample.BadContent -> badContent += 1
                Sample.Failed -> failures += 1
            }
        }
        if (latencies.isNotEmpty()) {
            val sorted = latencies.sorted()
            val median = if (sorted.size == 2) sorted[0] + (sorted[1] - sorted[0]) / 2 else sorted[sorted.size / 2]
            return MirrorProbeResult(
                status = ProbeStatus.Success,
                latencyMs = median,
                successes = latencies.size,
                detail = if (latencies.size == SAMPLES) "内容校验通过" else "仅统计内容校验通过的样本",
            )
        }
        return when {
            badContent > 0 -> failed("返回内容与固定样本不一致", ProbeStatus.InvalidContent)
            timeouts > failures -> failed("请求超时", ProbeStatus.Timeout)
            else -> failed("请求失败")
        }
    }

    private suspend fun attempt(startUrl: String): Sample = try {
        val response = withTimeoutOrNull(DEFAULT_TIMEOUT_MILLIS) {
            fetchWithRedirects(transport, startUrl, DEFAULT_TIMEOUT_MILLIS, DEFAULT_MAX_BYTES)
        }
        when {
            response == null -> Sample.Timeout
            response.statusCode !in 200..299 -> Sample.Failed
            response.body.size > DEFAULT_MAX_BYTES -> Sample.BadContent
            !matchesDigest(response.body, MirrorProbeTarget.sha256) -> Sample.BadContent
            else -> Sample.Ok(response.ttfbMs.coerceAtLeast(0))
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: MirrorTimeoutException) {
        Sample.Timeout
    } catch (_: Exception) {
        Sample.Failed
    }

    private fun failed(detail: String, status: ProbeStatus = ProbeStatus.Failed) =
        MirrorProbeResult(status, null, 0, detail)

    private sealed interface Sample {
        data class Ok(val ttfbMs: Long) : Sample
        data object Timeout : Sample
        data object BadContent : Sample
        data object Failed : Sample
    }

    private companion object {
        const val SAMPLES = 3
    }
}

internal fun digest(bytes: ByteArray): String = bytes.toByteString().sha256().hex()

/** Compares the full-body digest, not a length or a byte range. */
internal fun matchesDigest(bytes: ByteArray, expectedHex: String): Boolean =
    digest(bytes).equals(expectedHex.trim(), ignoreCase = true)
