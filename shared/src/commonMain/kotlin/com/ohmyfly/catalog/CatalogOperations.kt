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
    private data class Pending(val block: suspend () -> Unit, val done: CompletableDeferred<Unit>)
    private val queue = Channel<Pending>(8)
    private var active: Job? = null

    init {
        scope.launch {
            for (pending in queue) {
                update { it.copy(queuedBatches = it.queuedBatches - 1, failure = null) }
                active = scope.launch { execute(pending) }
                active!!.join()
                active = null
            }
        }
    }

    fun submit(block: suspend () -> Unit): Deferred<Unit> {
        val pending = Pending(block, CompletableDeferred())
        check(queue.trySend(pending).isSuccess) { "等待批次已满，请稍后再添加" }
        update { it.copy(busy = true, queuedBatches = it.queuedBatches + 1) }
        return pending.done
    }

    fun cancelAll() {
        var removed = 0
        while (true) {
            val pending = queue.tryReceive().getOrNull() ?: break
            pending.done.cancel()
            removed++
        }
        update { it.copy(queuedBatches = it.queuedBatches - removed, busy = active?.isActive == true) }
        active?.cancel()
    }

    private suspend fun execute(pending: Pending) {
        var failure: Throwable? = null
        var message: String? = null
        try { withContext(worker) { pending.block() }
        } catch (e: CancellationException) { failure = e; message = "操作已取消，已提交的数据保留"
        } catch (e: IllegalArgumentException) { failure = e; message = e.message ?: "导入内容无效"
        } catch (e: IllegalStateException) { failure = e; message = e.message ?: "目录更新未完成"
        } catch (e: Exception) { failure = e; message = "目录更新未完成，请重新读取目录确认状态"
        } finally {
            withContext(NonCancellable) {
                try { reload() }
                catch (e: Exception) { if (failure == null) { failure = e; message = "数据已提交，列表重读失败，请下拉重新读取" } }
                update { it.copy(busy = it.queuedBatches > 0, sourceTasks = emptyMap(), failure = message) }
                val error = failure
                if (error == null) pending.done.complete(Unit) else pending.done.completeExceptionally(error)
            }
        }
    }
}
