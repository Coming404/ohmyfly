// Ported from SubBoost local/src/lib/{source-import,snapshot-comparison}.ts @401534742802e2b1542783197ce66451bad82739 (AGPL-3.0-only).
// Ported from SubBoost packages/core/src/subscription/node-name-filter.ts @401534742802e2b1542783197ce66451bad82739 (AGPL-3.0-only).
package com.ohmyfly.subscription

import com.ohmyfly.nativecore.NativeSubscription
import com.ohmyfly.settings.SubscriptionSettings
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

internal data class SubscriptionNode(val name: String, val type: String, val server: String, val port: Int)
internal data class SubscriptionImportResult(val nodes: List<SubscriptionNode> = emptyList(), val failure: String? = null)
private data class ParsedAttempt(
    val parsed: NativeSubscription? = null, val failure: String? = null, val retryable: Boolean = false,
)

internal class SubscriptionImporter(
    private val fetcher: SubscriptionFetcher,
    private val parse: (String) -> NativeSubscription,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    suspend fun importSubscription(url: String, settings: SubscriptionSettings): SubscriptionImportResult {
        validateSubscriptionUrl(url)?.let { return SubscriptionImportResult(failure = it) }
        val target = url.trim()
        val timeoutMillis = (settings.timeoutSeconds * 1000L).coerceAtLeast(1)
        val budgetMillis = minOf(timeoutMillis * 4, 60_000L)
        val started = timeSource.markNow()
        var best: ParsedAttempt? = null
        attempts@ for (userAgent in subscriptionUserAgents(target, settings.userAgent)) {
            var retries = 0
            while (true) {
                val remaining = minOf(timeoutMillis, budgetMillis - started.elapsedNow().inWholeMilliseconds)
                if (remaining <= 0) break@attempts
                val attempt = fetchAndParse(target, settings, userAgent, remaining)
                best = pickBetterAttempt(best, attempt)
                // GitHub 默认模式明确只请求一次，连可重试错误也不再发起请求。
                if (!attempt.retryable || userAgent == null || retries >= settings.retries) break
                retries++
            }
        }
        val parsed = best?.parsed
        if (parsed == null) return SubscriptionImportResult(failure = best?.failure ?: "获取 url 失败")
        if (!hasUsableSubscriptionSnapshot(parsed)) {
            val failure = if (looksLikeClientUpdatePlaceholderNodes(parsed.nodes)) {
                "订阅服务返回了客户端更新提示占位内容，未导入该结果"
            } else "未解析到有效节点"
            return SubscriptionImportResult(failure = failure)
        }
        return filterNodes(parsed, settings)
    }

    private suspend fun fetchAndParse(
        url: String, settings: SubscriptionSettings, userAgent: String?, timeoutMillis: Long,
    ): ParsedAttempt {
        val content = try {
            fetcher.fetch(url, settings, userAgent, timeoutMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SubscriptionFetchException) {
            return ParsedAttempt(failure = e.message, retryable = e.retryable)
        } catch (_: Exception) {
            return ParsedAttempt(failure = "订阅请求失败")
        }
        return try {
            ParsedAttempt(parsed = parse(content))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // NativeCore 错误可能含订阅凭据，不把异常原文放进塔台状态。
            ParsedAttempt(failure = "订阅解析失败")
        }
    }
}

private fun pickBetterAttempt(current: ParsedAttempt?, next: ParsedAttempt): ParsedAttempt {
    if (current == null) return next
    val currentParsed = current.parsed
    val nextParsed = next.parsed
    val currentUsable = currentParsed != null && hasUsableSubscriptionSnapshot(currentParsed)
    val nextUsable = nextParsed != null && hasUsableSubscriptionSnapshot(nextParsed)
    if (currentUsable != nextUsable) return if (nextUsable) next else current
    if ((currentParsed != null) != (nextParsed != null)) return if (nextParsed != null) next else current
    if (currentParsed == null || nextParsed == null) return current
    return if (isBetterSubscriptionSnapshot(currentParsed, nextParsed)) next else current
}

private fun filterNodes(parsed: NativeSubscription, settings: SubscriptionSettings): SubscriptionImportResult {
    val includePattern = settings.includePattern.trim()
    val excludePattern = settings.excludePattern.trim()
    if (includePattern.length > 512 || excludePattern.length > 512) {
        return SubscriptionImportResult(failure = "塔台过滤规则不能超过 512 个字符")
    }
    val included = try {
        includePattern.takeIf(String::isNotEmpty)?.let { Regex(it, RegexOption.IGNORE_CASE) }
    } catch (_: IllegalArgumentException) {
        return SubscriptionImportResult(failure = "包含规则不是有效的正则表达式")
    }
    val excluded = try {
        excludePattern.takeIf(String::isNotEmpty)?.let { Regex(it, RegexOption.IGNORE_CASE) }
    } catch (_: IllegalArgumentException) {
        return SubscriptionImportResult(failure = "排除规则不是有效的正则表达式")
    }
    // shortcut: 不移植 SubBoost safeRegex，仅限制为 512 字符；接入可用的跨平台安全正则后再检查复杂度。
    val nodes = parsed.nodes.filter { node ->
        (included == null || included.containsMatchIn(node.name)) &&
            (excluded == null || !excluded.containsMatchIn(node.name)) &&
            (settings.protocols.isEmpty() || node.type in settings.protocols)
    }.map { SubscriptionNode(it.name, it.type, it.server, it.port) }
    return SubscriptionImportResult(nodes = nodes)
}
