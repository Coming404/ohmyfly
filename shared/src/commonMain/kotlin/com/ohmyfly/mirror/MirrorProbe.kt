// 镜像样本日志只记录来源和验证结果，不输出响应正文。
package com.ohmyfly.mirror

import com.ohmyfly.logging.logger
import com.ohmyfly.logging.logTarget

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

        logger.info { "[镜像探测] 开始 ${logTarget(target)}，样本 $SAMPLES 次，超时 ${DEFAULT_TIMEOUT_MILLIS}ms，上限 ${DEFAULT_MAX_BYTES}B" }
        val latencies = mutableListOf<Long>()
        var timeouts = 0
        var badContent = 0
        var failures = 0
        repeat(SAMPLES) {
            val sample = attempt(target)
            logger.debug {
                val result = when (sample) {
                    is Sample.Ok -> "内容校验通过，首字节等待 ${sample.ttfbMs} 毫秒"
                    Sample.Timeout -> "请求超时"
                    Sample.BadContent -> "返回内容不符合固定样本要求"
                    Sample.Failed -> "请求失败"
                }
                "[镜像探测] ${logTarget(target)} 第 ${it + 1} 次采样（共 $SAMPLES 次）：$result"
            }
            when (sample) {
                is Sample.Ok -> latencies += sample.ttfbMs
                Sample.Timeout -> timeouts += 1
                Sample.BadContent -> badContent += 1
                Sample.Failed -> failures += 1
            }
        }
        logger.info { "[镜像探测] ${logTarget(target)} 共采样 $SAMPLES 次，${latencies.size} 次通过内容校验，$timeouts 次超时，$badContent 次内容不符，$failures 次其他失败" }
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
        logger.debug {
            if (response == null) "[镜像探测] ${logTarget(startUrl)} 未在规定时间内返回"
            else "[镜像探测] ${logTarget(startUrl)} 返回 HTTP ${response.statusCode}，收到 ${response.body.size} 字节，首字节等待 ${response.ttfbMs} 毫秒；${if (matchesDigest(response.body, MirrorProbeTarget.sha256)) "SHA-256 内容校验通过" else "SHA-256 内容校验不一致"}"
        }
        when {
            response == null -> Sample.Timeout
            response.statusCode !in 200..299 -> Sample.Failed
            response.body.size > DEFAULT_MAX_BYTES -> Sample.BadContent
            !matchesDigest(response.body, MirrorProbeTarget.sha256) -> Sample.BadContent
            else -> Sample.Ok(response.ttfbMs.coerceAtLeast(0))
        }
    } catch (cancel: CancellationException) {
        logger.info { "[镜像探测] ${logTarget(startUrl)} 已取消" }
        throw cancel
    } catch (_: MirrorTimeoutException) {
        logger.debug { "[镜像探测] ${logTarget(startUrl)} 网络请求超时" }
        Sample.Timeout
    } catch (error: MirrorFetchException) {
        logger.debug { "[镜像探测] ${logTarget(startUrl)} 请求失败：${error.message}" }
        Sample.Failed
    } catch (error: Exception) {
        logger.debug { "[镜像探测] ${logTarget(startUrl)} 请求失败，异常类型为 ${error::class.simpleName}" }
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
