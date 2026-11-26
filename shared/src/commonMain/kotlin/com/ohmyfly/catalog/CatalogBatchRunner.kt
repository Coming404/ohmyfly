package com.ohmyfly.catalog

import com.ohmyfly.settings.SubscriptionSettings
import com.ohmyfly.subscription.*
import com.ohmyfly.logging.logger
import io.ktor.http.parseUrl
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.TimeSource

// 来源只持有地址或读取动作；大文件正文不进入等待队列。
internal data class CatalogImportSource(
    val address: String, val name: String,
    val read: (suspend () -> SubscriptionBody)? = null,
)

@Serializable
internal data class CatalogSource(
    val id: Long, val parentId: Long, val address: String, val name: String,
    val failure: String = "", val lastSuccessAt: Long? = null,
) {
    val local: Boolean get() = address.startsWith("local:")
    val kind: String get() = when {
        address.startsWith("local:file:") -> "file"
        address.startsWith("local:text:") -> "text"
        else -> "http"
    }
}

internal data class CatalogSourceTask(val parentId: Long, val phase: String, val name: String)

// 固定数量工作协程消费有界通道；解析许可只覆盖整份正文解析，不覆盖事务等待。
internal class CatalogBatchRunner(
    private val settings: SubscriptionSettings,
    private val mirrors: SubscriptionMirrorSettings,
    private val fetcher: SubscriptionFetcher,
    private val request: CatalogRequest,
    private val changed: suspend () -> Unit = {},
    private val finished: () -> Unit = {},
    private val update: ((CatalogState) -> CatalogState) -> Unit,
) {
    private val parses = Semaphore(settings.parseConcurrency)
    private val cancelledTowers = MutableStateFlow<Set<Long>>(emptySet())
    private val activeSources = MutableStateFlow<Map<Long, Pair<Long, Job>>>(emptyMap())
    private data class Work(val source: CatalogSource, val read: (suspend () -> SubscriptionBody)?)

    fun cancelTower(parentId: Long) {
        cancelledTowers.update { it + parentId }
        activeSources.value.values.filter { it.first == parentId }.forEach { it.second.cancel() }
    }

    suspend fun importSources(inputs: List<CatalogImportSource>, merge: Boolean, name: String, replaceId: Long? = null) {
        execute { queue ->
            if (replaceId != null) {
                enqueue(queue, Work(readSources(replaceId, 0).single(), inputs.single().read))
                return@execute
            }
            var parent = 0L
            for (chunk in sourceBatches(inputs)) {
                currentCoroutineContext().ensureActive()
                val result = request("addSources") {
                    put("id", parent); put("merge", merge); put("name", name)
                    put("sources", chunk.records)
                }
                val sources = catalogJson.decodeFromJsonElement<List<CatalogSource>>(result.getValue("sources"))
                if (merge) parent = sources.first().parentId
                // 来源落库后立即发布卡片，不等下载、解析和整批事务结束。
                publish()
                sources.zip(chunk.inputs).forEach { (source, input) -> enqueue(queue, Work(source, input.read)) }
            }
        }
    }

    // 所选塔台共用一个批次与工作池，页面离开或首个来源提交不会取消其余来源。
    suspend fun sync(parentIds: Collection<Long>, pendingRemoval: () -> Set<Long>) {
        execute { queue ->
            for (parentId in parentIds) {
                var anchor = 0L
                do {
                    currentCoroutineContext().ensureActive()
                    val batch = readSources(parentId, anchor)
                    for (source in batch) {
                        if (!source.local && source.parentId !in pendingRemoval() &&
                            source.parentId !in cancelledTowers.value) enqueue(queue, Work(source, null))
                    }
                    if (batch.size < 100) break
                    anchor = batch.last().id
                } while (true)
            }
        }
    }

    private fun readSources(id: Long, anchor: Long): List<CatalogSource> = catalogJson.decodeFromJsonElement(
        request("sources") { put("id", id); put("anchor", anchor) }.getValue("sources"))

    private suspend fun enqueue(queue: Channel<Work>, work: Work) {
        update { state ->
            val progress = state.towerTaskProgress[work.source.parentId] ?: CatalogTaskProgress()
            state.copy(taskTotal = state.taskTotal + 1,
                towerTaskProgress = state.towerTaskProgress + (work.source.parentId to progress.copy(total = progress.total + 1)),
                sourceTasks = state.sourceTasks + (work.source.id to CatalogSourceTask(
                    work.source.parentId, "排队", work.source.displayName())))
        }
        queue.send(work)
    }

    private suspend fun execute(produce: suspend (Channel<Work>) -> Unit) = coroutineScope {
        update { it.copy(taskTotal = 0, taskCompleted = 0, taskFailed = 0,
            towerTaskProgress = emptyMap(), sourceTasks = emptyMap()) }
        val queue = Channel<Work>(settings.towerConcurrency)
        val producer = launch { try { produce(queue) } finally { queue.close() } }
        val workers = List(settings.towerConcurrency) {
            launch { for (work in queue) processIsolated(work) }
        }
        try { producer.join(); workers.joinAll(); finished() }
        finally { queue.cancel(); update { it.copy(sourceTasks = emptyMap()) } }
    }

    private suspend fun processIsolated(work: Work) = supervisorScope {
        var started = false
        val task = launch(start = CoroutineStart.LAZY) {
            started = true
            process(work)
        }
        activeSources.update { it + (work.source.id to (work.source.parentId to task)) }
        task.start()
        if (work.source.parentId in cancelledTowers.value) task.cancel()
        try { task.join() }
        finally {
            activeSources.update { it - work.source.id }
            if (!started) completeSkipped(work)
        }
    }

    private suspend fun completeSkipped(work: Work) {
        withContext(NonCancellable) {
            update { state ->
                val progress = state.towerTaskProgress[work.source.parentId] ?: CatalogTaskProgress()
                state.copy(taskCompleted = state.taskCompleted + 1,
                    towerTaskProgress = state.towerTaskProgress +
                        (work.source.parentId to progress.copy(completed = progress.completed + 1)),
                    sourceTasks = state.sourceTasks - work.source.id)
            }
        }
    }

    // 网络结果与卡片重读分别报告，避免把已经提交的同步误判为失败。
    private suspend fun publish() {
        try { changed() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { logCatalogFailure("刷新塔台卡片", e) }
    }

    private suspend fun process(work: Work) {
        val source = work.source
        var stage = "准备"
        var stageStarted = TimeSource.Monotonic.markNow()
        val displayName = source.displayName()
        logger.info { "[塔台航班] 正在处理“$displayName”" }
        fun phase(value: String) {
            val elapsed = stageStarted.elapsedNow().inWholeMilliseconds
            logger.debug { "[塔台航班] “$displayName”阶段：$stage，耗时 ${elapsed} 毫秒" }
            stage = value
            stageStarted = TimeSource.Monotonic.markNow()
            logger.debug { "[塔台航班] “$displayName”阶段：$value" }
            update { state -> state.copy(sourceTasks = state.sourceTasks +
                (source.id to CatalogSourceTask(source.parentId, value, displayName))) }
        }
        var failed = false
        var publishAfter = true
        try {
            if (source.parentId in cancelledTowers.value) {
                publishAfter = false
                return
            }
            phase(if (work.read == null) "下载" else "读取")
            val importer = SubscriptionImporter(fetcher, { content ->
                phase("等待解析")
                parses.withPermit {
                    phase("解析")
                    try { parseCatalogSubscription(content) } finally { phase("处理") }
                }
            }, queue = ::CatalogProviderQueue)
            val service = CatalogRefreshService(importer, request) { phase("提交") }
            if (work.read != null) service.importBody(work.read.invoke(), settings, source.id)
            else if (!service.refresh(source.id, settings, mirrors)) {
                failed = true
                return
            }
            logger.info { "[塔台航班] “$displayName”同步完成" }
        } catch (e: CancellationException) {
            publishAfter = false
            logger.info { "[塔台航班] “$displayName”同步已取消" }
            throw e
        } catch (_: CatalogStaleException) {
            // 删除已使来源失效，不重新创建或提交旧结果。
        } catch (e: Exception) {
            failed = true
            val reason = logCatalogFailure("同步“$displayName”时的${stage}阶段", e)
            recordFailure(source.id, "$stage：$reason")
        } finally {
            val elapsed = stageStarted.elapsedNow().inWholeMilliseconds
            logger.debug { "[塔台航班] “$displayName”阶段：$stage，耗时 ${elapsed} 毫秒" }
            withContext(NonCancellable) {
                update { state ->
                    val progress = state.towerTaskProgress[source.parentId] ?: CatalogTaskProgress()
                    state.copy(taskCompleted = state.taskCompleted + 1,
                        taskFailed = state.taskFailed + if (failed) 1 else 0,
                        towerTaskProgress = state.towerTaskProgress +
                            (source.parentId to progress.copy(completed = progress.completed + 1)),
                        sourceTasks = state.sourceTasks - source.id)
                }
                if (publishAfter) publish()
            }
        }
    }

    private fun recordFailure(id: Long, failure: String) {
        try {
            val ticket = catalogJson.decodeFromJsonElement<RefreshTicket>(request("begin") { put("id", id) })
            request("fail") { put("id", id); put("generation", ticket.generation); put("name", ""); put("failure", failure) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logCatalogFailure("保存塔台失败状态", e)
        }
    }
}

private fun CatalogSource.displayName(): String = name.takeIf(String::isNotBlank) ?: when {
    local && kind == "file" -> "配置文件"
    local -> "粘贴内容"
    else -> parseUrl(address)?.host ?: "网络订阅"
}
