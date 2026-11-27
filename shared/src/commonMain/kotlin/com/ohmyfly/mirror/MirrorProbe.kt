// 测速日志只记录来源、HTTP 状态和响应头耗时。
package com.ohmyfly.mirror

import com.ohmyfly.logging.logger
import com.ohmyfly.logging.logTarget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/** A single HEAD response, including 3xx without following it; caller cancellation propagates. */
internal class MirrorProbe(private val transport: MirrorTransport) {
    suspend fun measure(entry: MirrorEntry?): MirrorProbeResult {
        val resource = "${MirrorProbeTarget.url}?_t=${Clock.System.now().toEpochMilliseconds()}"
        val target = entry?.let {
            validateMirror(it)?.let { error -> return failed(error) }
            buildMirrorUrl(it, resource)
        } ?: resource
        logger.debug { "[镜像探测] HEAD ${logTarget(target)}，超时 ${MirrorProbeTarget.timeoutMillis}ms" }
        return try {
            val response = withTimeoutOrNull(MirrorProbeTarget.timeoutMillis) {
                transport.head(target, MirrorProbeTarget.timeoutMillis)
            }
            if (response == null) {
                logger.debug { "[镜像探测] ${logTarget(target)} 未在规定时间内返回" }
                failed("请求超时", ProbeStatus.Timeout)
            } else {
                logger.debug { "[镜像探测] ${logTarget(target)} 返回 HTTP ${response.statusCode}，响应头耗时 ${response.latencyMs}ms" }
                if (response.statusCode in 200..399) {
                    MirrorProbeResult(ProbeStatus.Success, response.latencyMs, "HTTP ${response.statusCode}")
                } else {
                    failed("HTTP ${response.statusCode}")
                }
            }
        } catch (cancel: CancellationException) {
            logger.debug { "[镜像探测] ${logTarget(target)} 已取消" }
            throw cancel
        } catch (_: MirrorTimeoutException) {
            logger.debug { "[镜像探测] ${logTarget(target)} 网络请求超时" }
            failed("请求超时", ProbeStatus.Timeout)
        } catch (error: MirrorFetchException) {
            logger.debug { "[镜像探测] ${logTarget(target)} 请求失败：${error.message}" }
            failed("请求失败")
        } catch (error: Exception) {
            logger.debug { "[镜像探测] ${logTarget(target)} 请求失败，异常类型为 ${error::class.simpleName}" }
            failed("请求失败")
        }
    }

    private fun failed(detail: String, status: ProbeStatus = ProbeStatus.Failed) =
        MirrorProbeResult(status, null, detail)
}
