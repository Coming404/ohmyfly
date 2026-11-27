// Ported from SubBoost local/src/lib/{source-import,snapshot-comparison}.ts @401534742802e2b1542783197ce66451bad82739 (AGPL-3.0-only).
// Ported from SubBoost packages/core/src/subscription/node-name-filter.ts @401534742802e2b1542783197ce66451bad82739 (AGPL-3.0-only).
package com.ohmyfly.subscription

import com.ohmyfly.logging.logger
import com.ohmyfly.logging.newLogId
import com.ohmyfly.logging.catalogExceptionDetails

import com.ohmyfly.nativecore.NativeSubscription
import com.ohmyfly.nativecore.NativeSubscriptionNode
import com.ohmyfly.nativecore.close
import io.ktor.http.parseUrl
import kotlinx.serialization.json.JsonObject
import com.ohmyfly.settings.SubscriptionSettings
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

internal data class SubscriptionNode(
    val name: String, val type: String, val server: String, val port: Int,
    val fields: JsonObject = JsonObject(emptyMap()),
)
internal data class SubscriptionImportResult(
    val nodes: List<SubscriptionNode> = emptyList(), val failure: String? = null,
    val records: List<NativeSubscriptionNode> = emptyList(), val recordCount: Int = records.size,
    val warnings: List<String> = emptyList(), val usage: SubscriptionUsage? = null, val name: String? = null,
)
internal data class ParsedAttempt(
    val parsed: NativeSubscription? = null, val failure: String? = null, val retryable: Boolean = false,
    val usage: SubscriptionUsage? = null, val name: String? = null,
    val body: String? = null,
)

internal class SubscriptionImporter(
    private val fetcher: SubscriptionFetcher,
    private val parse: suspend (String) -> NativeSubscription,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val phase: (String) -> Unit = {},
    private val progress: (Int, Int) -> Unit = { _, _ -> },
    val queue: () -> ProviderQueue = ::MemoryProviderQueue,
) {
    suspend fun importSubscription(
        url: String, settings: SubscriptionSettings, resolveName: Boolean = true,
        mirrors: SubscriptionMirrorSettings = SubscriptionMirrorSettings(),
        accept: (suspend (NativeSubscriptionNode) -> Unit)? = null,
        displayName: String? = null,
        resolvedName: (String) -> Unit = {},
    ): SubscriptionImportResult {
        val operation = newLogId().take(8)
        val started = timeSource.markNow()
        val label = displayName?.takeIf(String::isNotBlank) ?: parseUrl(url)?.host ?: "订阅"
        logger.debug { "[订阅 $operation] 正在获取订阅：$label" }
        logger.debug { "[订阅 $operation] 每次请求最多等待 ${settings.timeoutSeconds} 秒，每个 UA 候选最多重试 ${settings.retries} 次，单次下载上限为 ${settings.maxMiB} MiB；整次导入共用 ${minOf(settings.timeoutSeconds * 4000L, 60_000L)} 毫秒时间预算" }
        return try {
            importContent(url, settings, operation, resolveName, mirrors, accept, resolvedName).also { result ->
                val resultName = displayName?.takeIf(String::isNotBlank) ?: result.name?.takeIf(String::isNotBlank)
                    ?: parseUrl(url)?.host ?: "订阅"
                val status = result.failure?.let { "获取失败：${subscriptionFailureDescription(it)}" }
                    ?: "获取完成，共处理 ${result.recordCount} 条节点记录"
                logger.debug { "[订阅 $operation] 塔台“$resultName”$status；${result.warnings.size} 条提示，耗时 ${started.elapsedNow().inWholeMilliseconds} 毫秒" }
            }
        } catch (cancelled: CancellationException) {
            logger.debug { "[订阅] “$label”同步已取消" }
            throw cancelled
        }
    }

    suspend fun importBody(
        content: String, settings: SubscriptionSettings,
        accept: suspend (NativeSubscriptionNode) -> Unit,
    ): SubscriptionImportResult {
        val filter = SubscriptionFilter(settings)
        val parsed = parse(content)
        try {
            if (!hasUsableSubscriptionSnapshot(parsed)) {
                return SubscriptionImportResult(failure = "未解析到有效节点；本地正文不会自动抓取外部 provider")
            }
            val excluded = IntArray(4)
            progress(0, parsed.nodes.size)
            for ((index, node) in parsed.nodes.withIndex()) {
                val reason = filter.reason(node.name, node.server, node.type)
                if (reason >= 0) excluded[reason]++
                accept(node.copy(eligible = reason < 0))
                if ((index + 1) % 100 == 0 || index + 1 == parsed.nodes.size) progress(index + 1, parsed.nodes.size)
            }
            logger.debug { "[订阅] 本地正文共解析 ${parsed.nodes.size} 个航班，过滤 ${excluded.sum()} 个（本机 ${excluded[0]}，未匹配包含 ${excluded[1]}，命中排除 ${excluded[2]}，协议 ${excluded[3]}），过滤后 ${parsed.nodes.size - excluded.sum()} 个" }
            val warnings = buildList {
                if (parsed.skipped > 0) add("跳过 ${parsed.skipped} 条无法解析的记录")
                if (parsed.proxyProviders.isNotEmpty()) add("本地正文中的外部 provider 未抓取，请单独添加订阅链接")
            }
            return SubscriptionImportResult(recordCount = parsed.nodes.size, warnings = warnings)
        } finally { parsed.close() }
    }

    private suspend fun importContent(
        url: String, settings: SubscriptionSettings, operation: String, resolveName: Boolean,
        mirrors: SubscriptionMirrorSettings,
        accept: (suspend (NativeSubscriptionNode) -> Unit)?,
        resolvedName: (String) -> Unit,
    ): SubscriptionImportResult {
        validateSubscriptionUrl(url)?.let { return SubscriptionImportResult(failure = it) }
        val target = url.trim()
        val budgetMillis = minOf(settings.timeoutSeconds * 4000L, 60_000L).coerceAtLeast(1)
        val started = timeSource.markNow()
        fun remaining() = budgetMillis - started.elapsedNow().inWholeMilliseconds
        val initial = fetchSnapshot(target, settings, emptyMap(), ::remaining, operation, resolveName, mirrors)
        val parsed = initial.parsed
            ?: return SubscriptionImportResult(failure = initial.failure ?: "获取 url 失败")
        try {
            resolvedName(initial.name?.takeIf(String::isNotBlank) ?: parseUrl(target)!!.host)
            if (accept != null) return streamContent(target, initial, settings, operation, ::remaining, mirrors, accept)
            val resolved = resolveSubscriptionProviders(target, parsed, parse, ::remaining, operation, queue = queue()) { providerUrl, headers ->
                fetchSnapshot(providerUrl, settings, headers, ::remaining, operation, resolveName = false, mirrors = mirrors)
            }
            val merged = parsed.copy(nodes = resolved.nodes)
            if (!hasUsableSubscriptionSnapshot(merged)) {
                val failure = if (looksLikeClientUpdatePlaceholderNodes(merged.nodes)) {
                    "订阅服务返回了客户端更新提示占位内容，未导入该结果"
                } else "未解析到有效节点"
                return SubscriptionImportResult(failure = failure, warnings = resolved.warnings)
            }
            return filterNodes(merged, settings, operation).copy(
                warnings = resolved.warnings, usage = initial.usage, name = initial.name,
            )
        } finally { parsed.close() }
    }

    private suspend fun streamContent(
        target: String, initial: ParsedAttempt, settings: SubscriptionSettings, operation: String,
        remaining: () -> Long, mirrors: SubscriptionMirrorSettings,
        accept: suspend (NativeSubscriptionNode) -> Unit,
    ): SubscriptionImportResult {
        val filter = try { SubscriptionFilter(settings) } catch (e: IllegalArgumentException) {
            return SubscriptionImportResult(failure = e.message)
        }
        val parsed = initial.parsed!!
        var count = 0
        val excluded = IntArray(4)
        var usable = false
        var firstPlaceholder = false
        val total = if (parsed.proxyProviders.isEmpty()) parsed.nodes.size else 0
        progress(0, total)
        val resolved = resolveSubscriptionProviders(target, parsed, parse, remaining, operation, queue = queue(), accept = { node ->
            count++
            val single = parsed.copy(nodes = listOf(node))
            if (count == 1) firstPlaceholder = looksLikeClientUpdatePlaceholderNodes(single.nodes)
            usable = usable || isUsableSubscriptionNodeName(node.name)
            val reason = filter.reason(node.name, node.server, node.type)
            if (reason >= 0) excluded[reason]++
            accept(node.copy(eligible = reason < 0))
            if (count % 100 == 0 || count == total) progress(count, total)
        }) { providerUrl, headers ->
            fetchSnapshot(providerUrl, settings, headers, remaining, operation, resolveName = false, mirrors = mirrors)
        }
        val failure = when {
            count == 1 && firstPlaceholder -> "订阅服务返回了客户端更新提示占位内容，未导入该结果"
            !usable -> "未解析到有效节点"
            else -> null
        }
        logger.debug { "[订阅 $operation] 获取 1 个塔台来源，共 $count 个航班；过滤 ${excluded.sum()} 个（本机 ${excluded[0]}，未匹配包含 ${excluded[1]}，命中排除 ${excluded[2]}，协议 ${excluded[3]}），过滤后 ${count - excluded.sum()} 个" }
        return SubscriptionImportResult(failure = failure, recordCount = count,
            warnings = resolved.warnings, usage = initial.usage, name = initial.name)
    }

    private suspend fun fetchSnapshot(
        url: String, settings: SubscriptionSettings, headers: Map<String, List<String>>,
        remainingBudget: () -> Long, operation: String, resolveName: Boolean,
        mirrors: SubscriptionMirrorSettings,
    ): ParsedAttempt {
        val timeoutMillis = (settings.timeoutSeconds * 1000L).coerceAtLeast(1)
        var best: ParsedAttempt? = null
        val providerAgent = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
        val agents = if (providerAgent != null) listOf(providerAgent.value.first())
            else subscriptionUserAgents(url, settings.userAgent)
        val urls = subscriptionRequestUrls(url, headers, mirrors)
        try {
        attempts@ for ((agentIndex, userAgent) in agents.withIndex()) {
            if (agentIndex > 0) {
                val pause = kotlin.random.Random.nextLong(
                    SubscriptionUserAgentDelayMinMillis, SubscriptionUserAgentDelayMaxMillis + 1,
                )
                if (remainingBudget() <= pause) break@attempts
                phase("等待请求")
                kotlinx.coroutines.delay(pause)
            }
            routes@ for (requestUrl in urls) {
                var retries = 0
                while (true) {
                    val remaining = minOf(timeoutMillis, remainingBudget())
                    if (remaining <= 0) {
                        logger.debug { "[订阅 $operation] 总时间预算耗尽，停止后续候选请求" }
                        break@attempts
                    }
                    logger.debug { "[订阅 $operation] 尝试第 ${agentIndex + 1} 个 UA 候选（共 ${agents.size} 个）：${subscriptionUserAgentDescription(userAgent)}；线路：${subscriptionRouteDescription(url, requestUrl, mirrors)}；这是该线路的第 ${retries + 1} 次请求，最多等待 ${remaining} 毫秒" }
                    phase("下载")
                    val attempt = fetchAndParse(requestUrl, settings, userAgent, remaining, headers, operation, resolveName,
                        publicMirror = requestUrl != url, previous = best)
                    phase("比较")
                    val selected = try { pickBetterAttempt(best, attempt) } catch (error: Exception) {
                        attempt.parsed?.close(); throw error
                    }
                    logger.debug { "[订阅 $operation] 已比较可用性和节点质量，${if (selected === attempt) "暂选本次结果" else "保留此前更合适的结果"}；本次解析 ${attempt.parsed?.nodes?.size ?: 0} 个节点，${attempt.failure?.let { "失败原因：${subscriptionFailureDescription(it)}" } ?: "请求和解析已完成"}" }
                    val usableAttempt = attempt.parsed?.let { hasUsableSubscriptionSnapshot(it) || it.proxyProviders.isNotEmpty() } == true
                    if (selected !== best) best?.parsed?.close()
                    if (selected !== attempt) attempt.parsed?.close()
                    best = selected
                    // 空内容或更新占位不阻断线路回退；继续比较其他 UA 的节点质量。
                    if (usableAttempt) {
                        break@routes
                    }
                    if (!attempt.retryable || retries >= settings.retries) {
                        if (attempt.failure != null) logger.debug {
                            val reason = when {
                                !attempt.retryable -> "该错误不适合重试"
                                else -> "已用完 ${settings.retries} 次重试机会"
                            }
                            "[订阅 $operation] 不再重试当前候选：$reason"
                        }
                        break
                    }
                    retries++
                    logger.debug { "[订阅 $operation] 因${subscriptionFailureDescription(attempt.failure.orEmpty())}，将以相同 UA 进行第 $retries 次重试" }
                }
            }
        }
        return best ?: ParsedAttempt(failure = "订阅导入已达到总超时")
        } catch (error: Exception) {
            best?.parsed?.close()
            throw error
        }
    }

    private suspend fun fetchAndParse(
        url: String, settings: SubscriptionSettings, userAgent: String?, timeoutMillis: Long,
        headers: Map<String, List<String>>, operation: String, resolveName: Boolean, publicMirror: Boolean,
        previous: ParsedAttempt?,
    ): ParsedAttempt {
        val content = try {
            fetcher.fetch(url, settings, userAgent, timeoutMillis, headers, operation, resolveName, publicMirror)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SubscriptionFetchException) {
            logger.debug { "[订阅 $operation] 请求失败：${subscriptionFailureDescription(e.message.orEmpty())}；${if (e.retryable) "该错误允许在剩余预算内重试" else "该错误不再重试"}" }
            return ParsedAttempt(failure = e.message, retryable = e.retryable)
        } catch (error: Exception) {
            logger.error { "[订阅 $operation] 网络请求失败：${catalogExceptionDetails(error)}" }
            return ParsedAttempt(failure = subscriptionRequestFailure(error))
        }
        // 保留全部 UA 请求与择优；正文完全一致时复用已解析会话，不重复展开节点。
        if (previous?.parsed != null && previous.body == content.body) {
            logger.debug { "[订阅 $operation] 正文与当前候选一致，复用 ${previous.parsed.parsed} 个节点的解析结果" }
            return previous
        }
        return try {
            val started = timeSource.markNow()
            logger.debug { "[订阅 $operation] 开始 Go 解析，输入 ${content.body.length} 字符" }
            val parsed = parse(content.body)
            logger.debug { "[订阅 $operation] 解析完成：检查了 ${parsed.candidates} 条候选，成功解析 ${parsed.parsed} 个节点，跳过 ${parsed.skipped} 次，耗时 ${started.elapsedNow().inWholeMilliseconds} 毫秒" }
            logger.debug {
                val formats = buildList {
                    if (parsed.yaml > 0) add("识别到 ${parsed.yaml} 份 YAML 内容")
                    if (parsed.links > 0) add("识别到 ${parsed.links} 份链接文本")
                    if (parsed.base64 > 0) add("尝试了 ${parsed.base64} 次 Base64 解码，共 ${parsed.base64Layers} 层")
                    if (parsed.proxyProviders.isNotEmpty()) add("发现 ${parsed.proxyProviders.size} 个子订阅引用")
                }
                "[订阅 $operation] 解析过程：${formats.joinToString("；").ifEmpty { "未识别出可处理的内容" }}"
            }
            if (parsed.skippedByReason.isNotEmpty()) logger.debug {
                "[订阅 $operation] 跳过原因：" + parsed.skippedByReason.entries.joinToString("；") { (reason, count) ->
                    "${subscriptionSkipDescription(reason)}，共 $count 次"
                }
            }
            ParsedAttempt(parsed = parsed, usage = content.usage, name = content.name, body = content.body)
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            logger.error { "[订阅 $operation] 解析失败：${catalogExceptionDetails(error)}" }
            // NativeCore 错误可能含订阅凭据，不把异常原文放进塔台状态。
            ParsedAttempt(failure = "订阅解析失败")
        }
    }
}

private fun pickBetterAttempt(current: ParsedAttempt?, next: ParsedAttempt): ParsedAttempt {
    if (current == null || current === next) return next
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
    val filter = try { SubscriptionFilter(settings) } catch (e: IllegalArgumentException) {
        return SubscriptionImportResult(failure = e.message)
    }
    // shortcut: 不移植 SubBoost safeRegex，仅限制为 512 字符；接入可用的跨平台安全正则后再检查复杂度。
    val counts = IntArray(4)
    val records = ArrayList<NativeSubscriptionNode>(parsed.nodes.size)
    val nodes = parsed.nodes.mapNotNull { node ->
        val reason = filter.reason(node.name, node.server, node.type)
        records += node.copy(eligible = reason == -1)
        if (reason >= 0) { counts[reason]++; null }
        else SubscriptionNode(node.name, node.type, node.server, node.port, node.fields)
    }
    logger.debug { "[订阅 $operation] 对 ${parsed.nodes.size} 个节点进行过滤：排除 ${counts[0]} 个本机地址、${counts[1]} 个未匹配包含规则的节点、${counts[2]} 个匹配排除规则的节点、${counts[3]} 个协议未被选中的节点，最终保留 ${nodes.size} 个节点" }
    return SubscriptionImportResult(nodes = nodes, records = records)
}
