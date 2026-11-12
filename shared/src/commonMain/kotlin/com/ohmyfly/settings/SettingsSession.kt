// 设置日志记录读写结果与变更类别，不序列化完整配置或敏感值。
package com.ohmyfly.settings

import com.ohmyfly.logging.logger

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ohmyfly.ui.AppMessageKind
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

/** 只在 UI 线程调用；生命周期由 PersistedSettings 的单个 LaunchedEffect 持有。 */
internal class SettingsSession(
    private val values: Flow<AppSettings>,
    initial: AppSettings? = null,
    private val update: suspend ((AppSettings) -> AppSettings) -> Unit,
) {
    var state by mutableStateOf(
        initial?.let { SettingsUiState(settings = it, confirmedSettings = it) } ?: SettingsUiState(),
    )
        private set

    private var confirmed: AppSettings? = initial
    private var scope: CoroutineScope? = null
    private var reader: Job? = null
    private var recovery: Job? = null
    var onMessage: (AppMessageKind, String) -> Unit = { _, _ -> }

    private class PendingChange(
        val transform: (AppSettings) -> AppSettings,
        val done: CompletableDeferred<SettingsWriteResult>?,
        val name: String?,
        val reportErrors: Boolean,
        val successText: String?,
    )

    private val pending = ArrayDeque<PendingChange>()

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

    /** 立即反馈、不等待落盘；旧调用可省略名称。 */
    fun change(name: String? = null, transform: (AppSettings) -> AppSettings) {
        enqueue(PendingChange(transform, null, name, reportErrors = true, successText = null))
    }

    fun change(transform: (AppSettings) -> AppSettings) = change(name = null, transform = transform)

    /** 只有事务提交并读回才返回 Confirmed；取消等待不会取消已入队写入。 */
    suspend fun changeConfirmed(
        name: String? = null,
        reportErrors: Boolean = true,
        successText: String? = null,
        transform: (AppSettings) -> AppSettings,
    ): SettingsWriteResult {
        val done = CompletableDeferred<SettingsWriteResult>()
        if (!enqueue(PendingChange(transform, done, name, reportErrors, successText))) return SettingsWriteResult.Rejected
        return done.await()
    }

    suspend fun changeConfirmed(transform: (AppSettings) -> AppSettings): SettingsWriteResult =
        changeConfirmed(name = null, transform = transform)

    private fun enqueue(change: PendingChange): Boolean {
        if (pending.isNotEmpty()) logger.debug { "[设置] ${change.name ?: "设置变更"}等待前面 ${pending.size} 项保存完成" }
        val activeScope = scope?.takeIf { it.isActive }
        if (activeScope == null || confirmed == null || state.readFailed) {
            logger.debug {
                val reason = when {
                    activeScope == null -> "设置会话尚未就绪或已经结束"
                    state.readFailed -> "配置读取失败，当前不可写"
                    else -> "尚未读取原配置"
                }
                "[设置] 无法保存${change.name ?: "设置变更"}：$reason"
            }
            if (change.reportErrors && !state.readFailed) {
                onMessage(AppMessageKind.Error, "${change.name ?: "设置"}暂时无法保存，请稍后重试")
            }
            return false
        }
        val next = change.transform(state.settings)
        val invalid = validateSettings(next)
        if (invalid != null) {
            logger.info { "[设置] ${change.name ?: "变更"}校验未通过：$invalid" }
            if (change.reportErrors) onMessage(AppMessageKind.Error, "${change.name ?: "设置"}无效：$invalid")
            return false
        }
        // 取消旧订阅；排空后重新订阅读当前磁盘值，不靠相等比较推测 Flow 的版本。
        if (pending.isEmpty()) reader?.cancel()
        pending.addLast(change)
        state = state.copy(settings = next, pendingWrites = pending.size)
        if (pending.size == 1) activeScope.launch { persist() }
        return true
    }

    /**
     * 读失败后的唯一恢复入口：只重读存储，不重放任何未确认操作，也不重新提交草稿。
     * 成功回到可写状态；仍失败就保持只读失败，草稿继续由页面 owner 持有。
     */
    fun retryRead() {
        val activeScope = scope?.takeIf { it.isActive } ?: return
        if (!state.readFailed || pending.isNotEmpty() || recovery?.isActive == true) return
        logger.info { "[设置] 开始重试读取，不重放未确认修改" }
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
                    writeUncertain = false,
                )
                logger.info { "[设置] 重试读取成功，恢复可写状态" }
                if (scope?.isActive == true) observe()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger.info { "[设置] 读取失败，异常类型为 ${error::class.simpleName}，保留原文件" }
                currentCoroutineContext().ensureActive()
                state = state.copy(readFailed = true)
            } finally {
                recovery = null
            }
        }
    }

    suspend fun refreshFromStorage() {
        check(pending.isEmpty()) { "设置正在保存，请稍后刷新" }
        val snapshot = state
        val stored = values.first()
        currentCoroutineContext().ensureActive()
        // 等待期间发生编辑或存储回调时，不用较早读取覆盖当前值；页面草稿保持原 owner。
        if (state !== snapshot || pending.isNotEmpty()) return
        confirmed = stored
        state = state.copy(settings = stored, confirmedSettings = stored, readFailed = false, writeUncertain = false)
        if (scope?.isActive == true) observe()
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
                        if (confirmed == null) logger.debug { "[设置] 首次读取持久化配置完成" }
                        else if (confirmed != value) logger.debug { "[设置] 检测到存储更新：${settingsLogChange(confirmed, value, "设置")}" }
                        confirmed = value
                        state = state.copy(
                            settings = value,
                            confirmedSettings = value,
                            pendingWrites = 0,
                            readFailed = false,
                            writeUncertain = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger.info { "[设置] 读取失败，异常类型为 ${error::class.simpleName}，保留原文件" }
                currentCoroutineContext().ensureActive()
                state = state.copy(readFailed = true)
            }
        }
    }

    private suspend fun persist() {
        try {
            while (pending.isNotEmpty()) {
                val before = confirmed
                try {
                    // 保留变换：由既有 DataStore 事务在最新存储值上合并，绝不整份覆盖。
                    update(pending.first().transform)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    rollback(reportFailure = true)
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
                val change = pending.removeFirst()
                val settled = confirmed
                if (settled == null) {
                    // 成功事务必然读回一个值；为空只可能来自异常读取，不能当作已确认。
                    state = state.copy(readFailed = true, writeUncertain = true, pendingWrites = 0)
                    change.done?.complete(SettingsWriteResult.Uncertain)
                    failRest()
                    return
                }
                state = state.copy(confirmedSettings = settled, pendingWrites = pending.size)
                logger.info { "[设置] 已保存并读回确认：${settingsLogChange(before, settled, change.name ?: "设置")}" }
                onMessage(AppMessageKind.Success, change.successText ?: "${change.name ?: "设置"}已保存")
                // 只有事务已提交且读回磁盘值，才把这次操作标记为已确认。
                change.done?.complete(SettingsWriteResult.Confirmed(settled))
                // 较早完成不发布 UI；所有点击的即时值持续有效直到队列排空。
            }
            state = state.copy(settings = confirmed ?: state.settings, confirmedSettings = confirmed, pendingWrites = 0)
        } catch (cancelled: CancellationException) {
            if (scope?.isActive == true) rollback(reportFailure = false)
            throw cancelled
        } finally {
            if (scope?.isActive == true && !state.readFailed && pending.isEmpty()) observe()
        }
    }

    /** 事务失败：失败操作及其后未执行的操作都失败，已确认值不受影响。 */
    private fun rollback(reportFailure: Boolean) {
        logger.info { "[设置] ${pending.firstOrNull()?.name ?: "设置"}${if (reportFailure) "保存失败" else "保存已取消"}，撤回 ${pending.size} 项未确认修改，已确认配置保持不变" }
        val failed = pending.firstOrNull()
        state = state.copy(settings = confirmed ?: state.settings, confirmedSettings = confirmed, pendingWrites = 0)
        if (reportFailure && failed != null && pending.any { it.reportErrors }) {
            val suffix = if (pending.size > 1) "；后续未保存的修改也已撤回" else ""
            val text = if (failed.reportErrors) "${failed.name ?: "设置"}保存失败，已恢复原值$suffix"
                else "因${failed.name ?: "前一项设置"}未保存，后续未保存的修改也已撤回"
            onMessage(AppMessageKind.Failure, text)
        }
        failRest()
    }

    /** 写后读取失败：该操作已提交但无法确认；阻断弹窗独占提示，后续操作直接失败。 */
    private fun uncertain() {
        logger.info { "[设置] 写入后读取失败，保存结果待确认，停止后续 ${pending.size - 1} 项修改" }
        state = state.copy(readFailed = true, writeUncertain = true, pendingWrites = 0)
        pending.removeFirstOrNull()?.done?.complete(SettingsWriteResult.Uncertain)
        failRest()
    }

    /** 队列里剩余调用方统一按 Failed 结束；它们没有提交，不能报成已确认。 */
    private fun failRest() {
        while (pending.isNotEmpty()) {
            pending.removeFirst().done?.complete(SettingsWriteResult.Failed)
        }
    }

    /** 会话结束：未完成操作按取消处理，不伪造成保存失败。 */
    private fun abandonPending() {
        while (pending.isNotEmpty()) {
            pending.removeFirst().done?.completeExceptionally(
                CancellationException("设置会话已结束，操作未确认"),
            )
        }
    }
}

private fun settingsLogChange(before: AppSettings?, after: AppSettings, name: String): String {
    if (before == after) return "配置未发生变化（本次保存：$name）"
    val changes = buildList {
        if (before?.mirrorEnabled != after.mirrorEnabled) add("镜像已${if (after.mirrorEnabled) "启用" else "停用"}")
        if (before?.selectedMirrorId != after.selectedMirrorId) {
            val index = after.mirrors.indexOfFirst { it.id == after.selectedMirrorId }
            add(if (index < 0) "未选择列表中的镜像" else "已选择列表第 ${index + 1} 个镜像")
        }
        if (before?.logging?.level != after.logging.level) add("日志记录级别改为${after.logging.level.label}")
        if (before?.logging?.fileMiB != after.logging.fileMiB) add("单个日志文件上限改为 ${after.logging.fileMiB} MiB")
        if (before?.logging?.totalMiB != after.logging.totalMiB) add("日志总容量上限改为 ${after.logging.totalMiB} MiB")
        if (before?.logging?.retentionDays != after.logging.retentionDays) add("日志保留时间改为 ${after.logging.retentionDays} 天")
        if (before?.themeMode != after.themeMode) add("主题改为${after.themeMode.label}")
        if (before?.materialMode != after.materialMode) add("材质改为${after.materialMode.label}")
        if (before?.reduceTransparency != after.reduceTransparency) add("减少透明效果已${if (after.reduceTransparency) "开启" else "关闭"}")
        if (before?.reduceMotion != after.reduceMotion) add("减少动态效果已${if (after.reduceMotion) "开启" else "关闭"}")
        if (before?.hapticEnabled != after.hapticEnabled) add("震动已${if (after.hapticEnabled) "开启" else "关闭"}")
        if (before?.hapticLevel != after.hapticLevel) add("震动强度改为${after.hapticLevel.label}")
        if (before?.mirrors != after.mirrors) add("镜像列表已更新，现有 ${after.mirrors.size} 个镜像")
        if (before?.speed != after.speed) add("测速设置已更新")
        if (before?.subscription != after.subscription) add("订阅设置已更新")
        if (before?.media != after.media) add("流媒体设置已更新")
    }
    return changes.joinToString("；").ifEmpty { "${name}已更新" }
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
