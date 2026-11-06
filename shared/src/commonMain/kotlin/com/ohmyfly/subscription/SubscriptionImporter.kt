// Ported from SubBoost local/src/lib/{source-import,snapshot-comparison}.ts @401534742802e2b1542783197ce66451bad82739 (AGPL-3.0-only).
// Ported from SubBoost packages/core/src/subscription/node-name-filter.ts @401534742802e2b1542783197ce66451bad82739 (AGPL-3.0-only).
package com.ohmyfly.subscription

import com.ohmyfly.logging.logger
import com.ohmyfly.logging.logTarget
import com.ohmyfly.logging.newLogId

import com.ohmyfly.nativecore.NativeSubscription
import com.ohmyfly.settings.SubscriptionSettings
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

internal data class SubscriptionNode(val name: String, val type: String, val server: String, val port: Int)
internal data class SubscriptionImportResult(
    val nodes: List<SubscriptionNode> = emptyList(), val failure: String? = null,
    val warnings: List<String> = emptyList(),
)
internal data class ParsedAttempt(
    val parsed: NativeSubscription? = null, val failure: String? = null, val retryable: Boolean = false,
)

internal class SubscriptionImporter(
    private val fetcher: SubscriptionFetcher,
    private val parse: (String) -> NativeSubscription,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    suspend fun importSubscription(url: String, settings: SubscriptionSettings): SubscriptionImportResult {
        val operation = newLogId().take(8)
        val started = timeSource.markNow()
        logger.info { "[订阅 $operation] 开始导入 ${logTarget(url)}" }
        logger.debug { "[订阅 $operation] 配置：单次超时 ${settings.timeoutSeconds}s，重试 ${settings.retries} 次，上限 ${settings.maxMiB}MiB，总预算 ${minOf(settings.timeoutSeconds * 4000L, 60_000L)}ms" }
        return try {
            importContent(url, settings, operation).also { result ->
                logger.info { "[订阅 $operation] 导入${if (result.failure == null) "成功" else "失败"}：节点 ${result.nodes.size}，提示 ${result.warnings.size}，原因 ${result.failure ?: "无"}，耗时 ${started.elapsedNow().inWholeMilliseconds}ms" }
            }
        } catch (cancelled: CancellationException) {
            logger.info { "[订阅 $operation] 导入已取消，耗时 ${started.elapsedNow().inWholeMilliseconds}ms" }
            throw cancelled
        }
    }

    private suspend fun importContent(url: String, settings: SubscriptionSettings, operation: String): SubscriptionImportResult {
        validateSubscriptionUrl(url)?.let { return SubscriptionImportResult(failure = it) }
        val target = url.trim()
        val budgetMillis = minOf(settings.timeoutSeconds * 4000L, 60_000L).coerceAtLeast(1)
        val started = timeSource.markNow()
        fun remaining() = budgetMillis - started.elapsedNow().inWholeMilliseconds
        val initial = fetchSnapshot(target, settings, emptyMap(), ::remaining, operation)
        val parsed = initial.parsed
            ?: return SubscriptionImportResult(failure = initial.failure ?: "获取 url 失败")
        val resolved = resolveSubscriptionProviders(target, parsed, parse, ::remaining, operation) { providerUrl, headers ->
            fetchSnapshot(providerUrl, settings, headers, ::remaining, operation)
        }
        val merged = parsed.copy(nodes = resolved.nodes)
        if (!hasUsableSubscriptionSnapshot(merged)) {
            val failure = if (looksLikeClientUpdatePlaceholderNodes(merged.nodes)) {
                "订阅服务返回了客户端更新提示占位内容，未导入该结果"
            } else "未解析到有效节点"
            return SubscriptionImportResult(failure = failure, warnings = resolved.warnings)
        }
        return filterNodes(merged, settings, operation).copy(warnings = resolved.warnings)
    }

    private suspend fun fetchSnapshot(
        url: String, settings: SubscriptionSettings, headers: Map<String, List<String>>,
        remainingBudget: () -> Long, operation: String,
    ): ParsedAttempt {
        val timeoutMillis = (settings.timeoutSeconds * 1000L).coerceAtLeast(1)
        var best: ParsedAttempt? = null
        val providerAgent = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
        val agents = if (providerAgent != null) listOf(providerAgent.value.first())
            else subscriptionUserAgents(url, settings.userAgent)
        attempts@ for ((agentIndex, userAgent) in agents.withIndex()) {
            var retries = 0
            while (true) {
                val remaining = minOf(timeoutMillis, remainingBudget())
                if (remaining <= 0) {
                    logger.debug { "[订阅 $operation] 总时间预算耗尽，停止后续候选请求" }
                    break@attempts
                }
                logger.debug { "[订阅 $operation] UA候选 ${agentIndex + 1}/${agents.size}，第 ${retries + 1} 次请求，本次超时 ${remaining}ms" }
                val attempt = fetchAndParse(url, settings, userAgent, remaining, headers, operation)
                val selected = pickBetterAttempt(best, attempt)
                logger.debug { "[订阅 $operation] 按可用性和节点质量比较候选：采用本次=${selected === attempt}，解析节点=${attempt.parsed?.nodes?.size ?: 0}，原因=${attempt.failure ?: "成功"}" }
                best = selected
                // GitHub 默认模式明确只请求一次，连可重试错误也不再发起请求。
                if (!attempt.retryable || userAgent == null || retries >= settings.retries) break
                retries++
            }
        }
        return best ?: ParsedAttempt(failure = "订阅导入已达到总超时")
    }

    private suspend fun fetchAndParse(
        url: String, settings: SubscriptionSettings, userAgent: String?, timeoutMillis: Long,
        headers: Map<String, List<String>>, operation: String,
    ): ParsedAttempt {
        val content = try {
            fetcher.fetch(url, settings, userAgent, timeoutMillis, headers, operation)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SubscriptionFetchException) {
            logger.info { "[订阅 $operation] 请求失败：${e.message}，可重试=${e.retryable}" }
            return ParsedAttempt(failure = e.message, retryable = e.retryable)
        } catch (error: Exception) {
            logger.info { "[订阅 $operation] 网络请求失败，异常类型=${error::class.simpleName}" }
            return ParsedAttempt(failure = "订阅请求失败")
        }
        return try {
            val started = timeSource.markNow()
            logger.debug { "[订阅 $operation] 开始 Go 解析，输入 ${content.length} 字符" }
            val parsed = parse(content)
            logger.debug { "[订阅 $operation] Go解析完成：候选=${parsed.candidates}，成功=${parsed.parsed}，跳过=${parsed.skipped}，YAML=${parsed.yaml}，链接=${parsed.links}，Base64=${parsed.base64}，编码层数=${parsed.base64Layers}，provider=${parsed.proxyProviders.size}，耗时=${started.elapsedNow().inWholeMilliseconds}ms" }
            logger.debug { "[订阅 $operation] 跳过原因统计：${parsed.skippedByReason.entries.groupBy { it.key.substringBefore(':') }.mapValues { (_, values) -> values.sumOf { it.value } }}" }
            ParsedAttempt(parsed = parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            logger.info { "[订阅 $operation] Go解析失败，异常类型=${error::class.simpleName}" }
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
    if (!currentUsable && currentParsed.proxyProviders.isEmpty() != nextParsed.proxyProviders.isEmpty()) {
        return if (nextParsed.proxyProviders.isNotEmpty()) next else current
    }
    return if (isBetterSubscriptionSnapshot(currentParsed, nextParsed)) next else current
}

private fun filterNodes(parsed: NativeSubscription, settings: SubscriptionSettings, operation: String): SubscriptionImportResult {
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
    val counts = IntArray(5)
    val seen = mutableSetOf<String>()
    val nodes = parsed.nodes.mapNotNull { node ->
        val reason = when {
            isLocalServer(node.server) -> 0
            included != null && !included.containsMatchIn(node.name) -> 1
            excluded != null && excluded.containsMatchIn(node.name) -> 2
            settings.protocols.isNotEmpty() && node.type !in settings.protocols -> 3
            !seen.add(node.fingerprint) -> 4
            else -> -1
        }
        if (reason >= 0) { counts[reason]++; null }
        else SubscriptionNode(node.name, node.type, node.server, node.port)
    }
    logger.debug { "[订阅 $operation] 过滤去重：输入=${parsed.nodes.size}，本机地址=${counts[0]}，未命中包含规则=${counts[1]}，命中排除规则=${counts[2]}，协议排除=${counts[3]}，配置重复=${counts[4]}，保留=${nodes.size}" }
    return SubscriptionImportResult(nodes = nodes)
}
