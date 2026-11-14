package com.ohmyfly.catalog

import com.ohmyfly.settings.SubscriptionSettings
import com.ohmyfly.subscription.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

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
    private val update: ((CatalogState) -> CatalogState) -> Unit,
) {
    private val parses = Semaphore(settings.parseConcurrency)
    private data class Work(val source: CatalogSource, val read: (suspend () -> SubscriptionBody)?)

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
                        if (!source.local && source.parentId !in pendingRemoval()) enqueue(queue, Work(source, null))
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
        update { it.copy(taskTotal = it.taskTotal + 1,
            sourceTasks = it.sourceTasks + (work.source.id to CatalogSourceTask(work.source.parentId, "排队", work.source.name.ifBlank { "来源 ${work.source.id}" }))) }
        queue.send(work)
    }

    private suspend fun execute(produce: suspend (Channel<Work>) -> Unit) = coroutineScope {
        update { it.copy(taskTotal = 0, taskCompleted = 0, taskFailed = 0, sourceTasks = emptyMap()) }
        val queue = Channel<Work>(settings.towerConcurrency)
        val producer = launch { try { produce(queue) } finally { queue.close() } }
        val workers = List(settings.towerConcurrency) {
            launch { for (work in queue) process(work) }
        }
        try { producer.join(); workers.joinAll() }
        finally { queue.cancel(); update { it.copy(sourceTasks = emptyMap()) } }
    }

    private suspend fun process(work: Work) {
        val source = work.source
        fun phase(value: String) = update { state -> state.copy(sourceTasks = state.sourceTasks +
            (source.id to CatalogSourceTask(source.parentId, value, source.name.ifBlank { "来源 ${source.id}" }))) }
        var failed = false
        try {
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
            else check(service.refresh(source.id, settings, mirrors)) { "来源同步失败，已保留上次成功数据" }
        } catch (e: CancellationException) { throw e
        } catch (_: CatalogStaleException) {
            // 删除已使来源失效，不重新创建或提交旧结果。
        } catch (e: Exception) {
            failed = true
            recordFailure(source.id, if (e is IllegalArgumentException) e.message ?: "来源内容无效" else "来源处理失败，已保留上次成功数据")
        } finally {
            update { it.copy(taskCompleted = it.taskCompleted + 1, taskFailed = it.taskFailed + if (failed) 1 else 0,
                sourceTasks = it.sourceTasks - source.id) }
        }
    }

    private fun recordFailure(id: Long, failure: String) {
        try {
            val ticket = catalogJson.decodeFromJsonElement<RefreshTicket>(request("begin") { put("id", id) })
            request("fail") { put("id", id); put("generation", ticket.generation); put("name", ""); put("failure", failure) }
        } catch (_: CatalogStaleException) {
            // 来源已删除时无需再保存失败状态。
        }
    }
}
