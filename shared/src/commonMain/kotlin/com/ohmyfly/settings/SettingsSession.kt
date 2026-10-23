package com.ohmyfly.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SAVE_ERROR = "保存失败，已恢复上次确认的设置；本次及后续更改未保存。请检查输入或设备存储后重新选择重试。"

/** 只在 UI 线程调用；生命周期由 PersistedSettings 的单个 LaunchedEffect 持有。 */
internal class SettingsSession(
    private val values: Flow<AppSettings>,
    private val update: suspend ((AppSettings) -> AppSettings) -> Unit,
) {
    var state by mutableStateOf(SettingsUiState())
        private set

    private var confirmed: AppSettings? = null
    private var scope: CoroutineScope? = null
    private var reader: Job? = null
    private var recovery: Job? = null
    private val pending = ArrayDeque<(AppSettings) -> AppSettings>()

    // 与 pending 逐项对齐：null 表示不等待结果的 change()，否则是等待真实确认的调用方。
    private val confirmations = ArrayDeque<CompletableDeferred<SettingsWriteResult>?>()

    suspend fun run(): Unit = coroutineScope {
        scope = this
        try {
            observe()
            awaitCancellation()
        } finally {
            scope = null
            abandonPending()
            pending.clear()
        }
    }

    /** 既有入口：立即反馈、不等待落盘；签名与语义保持不变。 */
    fun change(transform: (AppSettings) -> AppSettings) {
        val activeScope = scope?.takeIf { it.isActive } ?: return
        if (state.readFailed) return
        val current = state.settings ?: return
        val next = transform(current)
        val invalid = validateSettings(next)
        if (invalid != null) {
            state = state.copy(error = invalid)
            return
        }
        // 取消旧订阅；排空后重新订阅读当前磁盘值，不靠相等比较推测 Flow 的版本。
        if (pending.isEmpty()) reader?.cancel()
        pending.addLast(transform)
        confirmations.addLast(null)
        state = state.copy(settings = next, error = null, pendingWrites = pending.size)
        if (pending.size == 1) activeScope.launch { persist() }
    }

    /**
     * 走同一条顺序事务队列，但只有事务提交并读回磁盘值才返回 [SettingsWriteResult.Confirmed]。
     * 乐观快照仍然同步发布，调用方不能把 [SettingsUiState.settings] 当成这个结果。
     * 调用方取消只取消等待，已入队操作仍按顺序执行；CancellationException 原样上抛。
     */
    suspend fun changeConfirmed(transform: (AppSettings) -> AppSettings): SettingsWriteResult {
        val activeScope = scope?.takeIf { it.isActive } ?: return SettingsWriteResult.Rejected
        if (state.readFailed) return SettingsWriteResult.Rejected
        val current = state.settings ?: return SettingsWriteResult.Rejected
        val next = transform(current)
        val invalid = validateSettings(next)
        if (invalid != null) {
            state = state.copy(error = invalid)
            return SettingsWriteResult.Rejected
        }
        if (pending.isEmpty()) reader?.cancel()
        val done = CompletableDeferred<SettingsWriteResult>()
        pending.addLast(transform)
        confirmations.addLast(done)
        state = state.copy(settings = next, error = null, pendingWrites = pending.size)
        if (pending.size == 1) activeScope.launch { persist() }
        return done.await()
    }

    fun dismissError() {
        state = state.copy(error = null)
    }

    /**
     * 读失败后的唯一恢复入口：只重读存储，不重放任何未确认操作，也不重新提交草稿。
     * 成功回到可写状态；仍失败就保持只读失败，草稿继续由页面 owner 持有。
     */
    fun retryRead() {
        val activeScope = scope?.takeIf { it.isActive } ?: return
        if (!state.readFailed || pending.isNotEmpty() || recovery?.isActive == true) return
        recovery = activeScope.launch {
            reader?.cancel()
            try {
                val stored = values.first()
                currentCoroutineContext().ensureActive()
                confirmed = stored
                state = state.copy(
                    settings = stored,
                    confirmedSettings = stored,
                    pendingWrites = 0,
                    readFailed = false,
                    error = null,
                )
                if (scope?.isActive == true) observe()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                state = state.copy(readFailed = true)
            } finally {
                recovery = null
            }
        }
    }

    private fun observe() {
        reader?.cancel()
        reader = checkNotNull(scope).launch {
            try {
                values.collect { value ->
                    currentCoroutineContext().ensureActive()
                    // Cancellation is cooperative: a collector may still deliver one value after
                    // cancel(). Never let that stale read overwrite a pending optimistic choice or
                    // the rollback baseline while a save is in flight.
                    if (pending.isEmpty()) {
                        confirmed = value
                        state = state.copy(
                            settings = value,
                            confirmedSettings = value,
                            pendingWrites = 0,
                            readFailed = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                state = state.copy(readFailed = true)
            }
        }
    }

    private suspend fun persist() {
        try {
            while (pending.isNotEmpty()) {
                try {
                    // 保留变换：由既有 DataStore 事务在最新存储值上合并，绝不整份覆盖。
                    update(pending.first())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    rollback(SAVE_ERROR)
                    return
                }
                currentCoroutineContext().ensureActive()
                try {
                    // 新订阅取得实际已提交且经 Codec 解码的值，供失败回退与真实确认使用。
                    confirmed = values.first()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    uncertain()
                    return
                }
                currentCoroutineContext().ensureActive()
                pending.removeFirst()
                val done = confirmations.removeFirst()
                val settled = confirmed
                if (settled == null) {
                    // 成功事务必然读回一个值；为空只可能来自异常读取，不能当作已确认。
                    state = state.copy(readFailed = true, pendingWrites = 0)
                    done?.complete(SettingsWriteResult.Uncertain)
                    failRest()
                    return
                }
                state = state.copy(confirmedSettings = settled, pendingWrites = pending.size)
                // 只有事务已提交且读回磁盘值，才把这次操作标记为已确认。
                done?.complete(SettingsWriteResult.Confirmed(settled))
                // 较早完成不发布 UI；所有点击的即时值持续有效直到队列排空。
            }
            state = state.copy(settings = confirmed, confirmedSettings = confirmed, pendingWrites = 0)
        } catch (cancelled: CancellationException) {
            if (scope?.isActive == true) rollback(null)
            throw cancelled
        } finally {
            if (scope?.isActive == true && !state.readFailed && pending.isEmpty()) observe()
        }
    }

    /** 事务失败：失败操作及其后未执行的操作都失败，已确认值不受影响。 */
    private fun rollback(error: String?) {
        state = state.copy(settings = confirmed, error = error, confirmedSettings = confirmed, pendingWrites = 0)
        pending.clear()
        failRest()
    }

    /** 写后读取失败：该操作已提交但无法确认；其后未执行的操作直接失败，并阻止新写入。 */
    private fun uncertain() {
        state = state.copy(readFailed = true, pendingWrites = 0)
        pending.clear()
        confirmations.removeFirstOrNull()?.complete(SettingsWriteResult.Uncertain)
        failRest()
    }

    /** 队列里剩余调用方统一按 Failed 结束；它们没有提交，不能报成已确认。 */
    private fun failRest() {
        while (confirmations.isNotEmpty()) {
            confirmations.removeFirst()?.complete(SettingsWriteResult.Failed)
        }
    }

    /** 会话结束：未完成操作按取消处理，不伪造成保存失败。 */
    private fun abandonPending() {
        while (confirmations.isNotEmpty()) {
            confirmations.removeFirst()?.completeExceptionally(
                CancellationException("设置会话已结束，操作未确认"),
            )
        }
    }
}

/** 一次写入的真实结果；[SettingsUiState.settings] 的乐观值不等于这里的结果。 */
internal sealed interface SettingsWriteResult {
    /** 事务已提交，并返回随后读回的磁盘值（可能含其他字段或编码规范化后的值）。 */
    data class Confirmed(val settings: AppSettings) : SettingsWriteResult

    /** 未生效：事务失败，或同一队列中更早的操作失败导致它未被尝试。 */
    data object Failed : SettingsWriteResult

    /** 事务已返回但读回确认失败，磁盘结果未知；重读成功前阻止新写入。 */
    data object Uncertain : SettingsWriteResult

    /** 未入队：会话未运行、只读失败中、无当前值或变换未通过校验。 */
    data object Rejected : SettingsWriteResult
}
