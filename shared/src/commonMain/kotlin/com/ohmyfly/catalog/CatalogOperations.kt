package com.ohmyfly.catalog

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

// 批次按提交顺序执行；批次内部由来源/解析工作池并发，避免各批次叠加突破全局上限。
internal class CatalogOperations(
    private val scope: CoroutineScope,
    private val worker: CoroutineDispatcher,
    private val update: ((CatalogState) -> CatalogState) -> Unit,
    private val reload: suspend () -> Unit,
) {
    private data class Pending(val block: suspend () -> Unit, val done: CompletableDeferred<Unit>, val generation: Long)
    private val queue = Channel<Pending>(8)
    private val keyed = mutableMapOf<String, Deferred<Unit>>()
    private var generation = 0L
    private var active: Job? = null

    init {
        scope.launch {
            for (pending in queue) {
                // 接收器已获派但还未开始执行的批次也属于「全部停止」。
                if (pending.generation != generation) {
                    pending.done.cancel()
                    update { it.copy(queuedBatches = it.queuedBatches - 1, busy = it.queuedBatches > 1) }
                    continue
                }
                update { it.copy(queuedBatches = it.queuedBatches - 1, failure = null,
                    stopped = false, taskTotal = 0, taskCompleted = 0, taskFailed = 0) }
                active = scope.launch(start = CoroutineStart.UNDISPATCHED) { execute(pending) }
                active!!.join()
                active = null
            }
        }
    }

    fun submit(key: String? = null, block: suspend () -> Unit): Deferred<Unit> {
        // 同一同步尚未完成时直接返回原任务，避免连点生成多次网络请求。
        key?.let {
            val existing = if (it.startsWith("sync:")) keyed["sync:all"] ?: keyed[it] else keyed[it]
            existing?.takeUnless { job -> job.isCompleted }?.let { job -> return job }
        }
        val pending = Pending(block, CompletableDeferred(), generation)
        check(queue.trySend(pending).isSuccess) { "等待批次已满，请稍后再添加" }
        if (key != null) keyed[key] = pending.done
        pending.done.invokeOnCompletion { if (key != null && keyed[key] === pending.done) keyed.remove(key) }
        update { it.copy(busy = true, stopped = false, queuedBatches = it.queuedBatches + 1) }
        return pending.done
    }

    fun cancelAll() {
        generation++
        var removed = 0
        while (true) {
            val pending = queue.tryReceive().getOrNull() ?: break
            pending.done.cancel()
            removed++
        }
        val running = active?.isCompleted == false
        update { it.copy(queuedBatches = it.queuedBatches - removed, busy = running,
            stopping = running, stopped = !running) }
        active?.cancel()
    }

    private suspend fun execute(pending: Pending) {
        var failure: Throwable? = null
        var message: String? = null
        try { withContext(worker) { pending.block() }
        } catch (e: CancellationException) { failure = e
        } catch (e: Exception) {
            failure = e; message = logCatalogFailure("批次处理", e)
        } finally {
            withContext(NonCancellable) {
                try { reload() }
                catch (e: Exception) {
                    val reason = logCatalogFailure("同步后刷新列表", e)
                    if (failure == null || failure is CancellationException) {
                        failure = e
                        message = "列表重读失败：$reason"
                    }
                }
                update { it.copy(busy = it.queuedBatches > 0, sourceTasks = emptyMap(), failure = message,
                    stopped = it.stopping, stopping = false) }
                val error = failure
                if (error == null) pending.done.complete(Unit) else pending.done.completeExceptionally(error)
            }
        }
    }
}
