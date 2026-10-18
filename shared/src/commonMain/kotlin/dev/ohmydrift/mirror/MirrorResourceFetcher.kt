package dev.ohmydrift.mirror

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real request with ordered fallback: the selected mirror first, then the persisted list order,
 * then direct. Every candidate is tried at most once, so direct is never repeated.
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
    ): MirrorFetchResult {
        githubResourceError(originalUrl)?.let { throw MirrorFetchException(it) }
        require(timeoutMillis > 0) { "超时必须为正数" }
        require(maxBytes > 0) { "大小上限必须为正数" }

        val selectedIndex = selectedMirrorId?.let { id ->
            mirrors.indexOfFirst { it.id == id }.also {
                if (it < 0) throw MirrorFetchException("选中的镜像不存在")
            }
        }
        val ordered = buildList<MirrorEntry?> {
            if (selectedIndex == null) add(null) else add(mirrors[selectedIndex])
            mirrors.forEachIndexed { index, entry -> if (index != selectedIndex) add(entry) }
            if (selectedIndex != null) add(null)
        }.distinctBy { it?.id ?: DIRECT_MIRROR_ID }

        val failed = mutableListOf<String>()
        for (entry in ordered) {
            val key = entry?.id ?: DIRECT_MIRROR_ID
            if (entry != null && validateMirror(entry) != null) {
                failed += key
                continue
            }
            val url = entry?.let { buildMirrorUrl(it, originalUrl) } ?: originalUrl
            when (val outcome = attempt(url, timeoutMillis, maxBytes, expectedSha256)) {
                is Outcome.Ok -> return MirrorFetchResult(outcome.response, entry?.id, failed.toList())
                is Outcome.Fail -> failed += key
            }
        }
        throw MirrorFetchException("所有线路均失败（共尝试 ${failed.size} 条），请检查网络或稍后重试")
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
            } ?: return Outcome.Fail
            if (response.statusCode !in 200..299 || response.body.size > maxBytes) return Outcome.Fail
            if (expectedSha256 != null && !matchesDigest(response.body, expectedSha256)) return Outcome.Fail
            Outcome.Ok(response)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: MirrorTimeoutException) {
            Outcome.Fail
        } catch (_: Exception) {
            Outcome.Fail
        }
    }

    private sealed interface Outcome {
        data class Ok(val response: ProbeResponse) : Outcome
        data object Fail : Outcome
    }
}
