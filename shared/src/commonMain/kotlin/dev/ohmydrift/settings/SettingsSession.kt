package dev.ohmydrift.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
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
    private val pending = ArrayDeque<(AppSettings) -> AppSettings>()

    suspend fun run(): Unit = coroutineScope {
        scope = this
        try {
            observe()
            awaitCancellation()
        } finally {
            scope = null
            pending.clear()
        }
    }

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
        state = state.copy(settings = next, error = null)
        if (pending.size == 1) activeScope.launch { persist() }
    }

    fun dismissError() {
        state = state.copy(error = null)
    }

    private fun observe() {
        reader = checkNotNull(scope).launch {
            try {
                values.collect { value ->
                    currentCoroutineContext().ensureActive()
                    // Cancellation is cooperative: a collector may still deliver one value after
                    // cancel(). Never let that stale read overwrite a pending optimistic choice or
                    // the rollback baseline while a save is in flight.
                    if (pending.isEmpty()) {
                        confirmed = value
                        state = state.copy(settings = value, readFailed = false)
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
                    // 新订阅取得实际已提交且经 Codec 解码的值，供后续失败回退使用。
                    confirmed = values.first()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    pending.clear()
                    state = state.copy(readFailed = true)
                    return
                }
                currentCoroutineContext().ensureActive()
                pending.removeFirst()
                // 较早完成不发布 UI；所有点击的即时值持续有效直到队列排空。
            }
            state = state.copy(settings = confirmed)
        } catch (cancelled: CancellationException) {
            if (scope?.isActive == true) rollback(null)
            throw cancelled
        } finally {
            if (scope?.isActive == true && !state.readFailed) observe()
        }
    }

    private fun rollback(error: String?) {
        pending.clear()
        state = state.copy(settings = confirmed, error = error)
    }
}
