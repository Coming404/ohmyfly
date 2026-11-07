// 记录镜像尝试顺序及实际回退结果。
package com.ohmyfly.mirror

import com.ohmyfly.logging.logger
import com.ohmyfly.logging.logTarget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real request with ordered fallback: the selected mirror first, then the persisted list order,
 * then direct. Every candidate is tried at most once, so direct is never repeated.
 *
 * [enabled] is the persisted mirror switch: when false only the direct request is attempted and
 * the mirror list plus any stale selection are ignored instead of raising "选中镜像不存在".
 * URL safety checks, the size/digest limits and caller cancellation are unchanged either way.
 *
 * The caller's cancellation aborts immediately and is never downgraded into a fallback.
 */
internal class MirrorResourceFetcher(private val transport: MirrorTransport) {

    suspend fun fetch(
        originalUrl: String,
        selectedMirrorId: String?,
        mirrors: List<MirrorEntry>,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        maxBytes: Int = DEFAULT_MAX_BYTES,
        expectedSha256: String? = null,
        enabled: Boolean = true,
    ): MirrorFetchResult {
        logger.info { "[资源] 获取 ${logTarget(originalUrl)}；${if (enabled) "已启用镜像，列表中有 ${mirrors.size} 个候选" else "镜像已停用，仅使用直连"}；最多等待 ${timeoutMillis} 毫秒，最多接收 ${maxBytes} 字节；${if (expectedSha256 != null) "将校验内容摘要" else "未指定内容摘要"}" }
        githubResourceError(originalUrl)?.let { throw MirrorFetchException(it) }
        require(timeoutMillis > 0) { "超时必须为正数" }
        require(maxBytes > 0) { "大小上限必须为正数" }
        if (!enabled) return directOnly(originalUrl, timeoutMillis, maxBytes, expectedSha256)

        val selectedIndex = mirrors.indexOfFirst { it.id == selectedMirrorId }
        val ordered = buildList<MirrorEntry?> {
            if (selectedIndex >= 0) add(mirrors[selectedIndex])
            mirrors.forEachIndexed { index, entry -> if (index != selectedIndex) add(entry) }
            // No direct option in the UI: direct is attempted only after every mirror fails.
            add(null)
        }.distinctBy { it?.id ?: DIRECT_MIRROR_ID }

        val failed = mutableListOf<String>()
        for (entry in ordered) {
            val key = entry?.id ?: DIRECT_MIRROR_ID
            if (entry != null && validateMirror(entry) != null) {
                failed += key
                continue
            }
            val url = entry?.let { buildMirrorUrl(it, originalUrl) } ?: originalUrl
            logger.debug { "[资源] 尝试${if (entry == null) "直连" else "镜像"} ${logTarget(url)}，此前失败 ${failed.size} 条" }
            when (val outcome = attempt(url, timeoutMillis, maxBytes, expectedSha256)) {
                is Outcome.Ok -> {
                    logger.info { "[资源] 获取成功 ${logTarget(url)}，HTTP ${outcome.response.statusCode}，${outcome.response.body.size}B，此前失败 ${failed.size} 条" }
                    return MirrorFetchResult(outcome.response, entry?.id, failed.toList())
                }
                is Outcome.Fail -> {
                    logger.debug { "[资源] 当前线路失败，继续下一候选" }
                    failed += key
                }
            }
        }
        logger.info { "[资源] 全部候选失败，共 ${failed.size} 条" }
        throw MirrorFetchException("所有线路均失败（共尝试 ${failed.size} 条），请检查网络或稍后重试")
    }

    /** 停用镜像：不构建任何镜像 URL，也不用已保留的选择做判断，只发一次直连请求。 */
    private suspend fun directOnly(
        originalUrl: String,
        timeoutMillis: Long,
        maxBytes: Int,
        expectedSha256: String?,
    ): MirrorFetchResult {
        logger.debug { "[资源] 镜像已停用，仅尝试直连 ${logTarget(originalUrl)}" }
        return when (val outcome = attempt(originalUrl, timeoutMillis, maxBytes, expectedSha256)) {
            is Outcome.Ok -> {
                logger.info { "[资源] 直连获取成功，${outcome.response.body.size}B" }
                MirrorFetchResult(outcome.response, null, emptyList())
            }
            is Outcome.Fail -> {
                logger.info { "[资源] 镜像已停用且直连失败" }
                throw MirrorFetchException("镜像已停用，直连请求失败，请检查网络或稍后重试")
            }
        }
    }

    private suspend fun attempt(
        url: String,
        timeoutMillis: Long,
        maxBytes: Int,
        expectedSha256: String?,
    ): Outcome {
        return try {
            val response = withTimeoutOrNull(timeoutMillis) {
                fetchWithRedirects(transport, url, timeoutMillis, maxBytes)
            }
            val failure = when {
                response == null -> "请求超时"
                response.statusCode !in 200..299 -> "HTTP ${response.statusCode}"
                response.body.size > maxBytes -> "响应超过 ${maxBytes}B"
                expectedSha256 != null && !matchesDigest(response.body, expectedSha256) -> "SHA256内容校验不一致"
                else -> null
            }
            if (failure != null) {
                logger.debug { "[资源] ${logTarget(url)} 失败：$failure" }
                return Outcome.Fail
            }
            Outcome.Ok(checkNotNull(response))
        } catch (cancel: CancellationException) {
            logger.info { "[资源] 请求已取消，不再尝试后续线路" }
            throw cancel
        } catch (_: MirrorTimeoutException) {
            logger.debug { "[资源] ${logTarget(url)} 网络请求超时" }
            Outcome.Fail
        } catch (error: MirrorFetchException) {
            logger.debug { "[资源] ${logTarget(url)} 请求失败：${error.message}" }
            Outcome.Fail
        } catch (error: Exception) {
            logger.debug { "[资源] ${logTarget(url)} 请求失败，异常类型为 ${error::class.simpleName}" }
            Outcome.Fail
        }
    }

    private sealed interface Outcome {
        data class Ok(val response: ProbeResponse) : Outcome
        data object Fail : Outcome
    }
}
