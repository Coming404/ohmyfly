package com.ohmyfly

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ohmyfly.logging.AppLog
import com.ohmyfly.logging.logger
import kotlinx.coroutines.CancellationException
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.settings.createSettingsRepository
import com.ohmyfly.ui.AppMessageState
import com.ohmyfly.ui.LocalAppMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

/**
 * 设置读取与保存的唯一入口：点击经校验后由 [SettingsSession] 立即更新界面，
 * DataStore 事务在后台排队完成；向镜像页透传同一个会话以等待真实写入确认。
 */
@Composable
internal fun PersistedSettings(
    path: String,
    content: @Composable (AppSettings, (String, (AppSettings) -> AppSettings) -> Unit, SettingsSession, () -> Unit) -> Unit,
) {
    val messages = remember { AppMessageState() }
    var retry by remember { mutableStateOf(0) }
    val repository = remember(path, retry) { runCatching { createSettingsRepository(path) }.getOrNull() }
    val session = remember(path, repository, retry) {
        // Block the first composition on the stored values so the first frame already uses the
        // chosen material; the launch screen stays up meanwhile. Failures fall through to the
        // async reader, which reports them through readFailed.
        val initial = repository?.let { repo -> runCatching { runBlocking(Dispatchers.IO) { repo.data.first() } }.getOrNull() }
        (repository?.let { repo -> SettingsSession(repo.data, initial, repo::update) }
            ?: SettingsSession(flow { error("设置存储不可用") }) { error("设置存储不可用") })
            .apply { onMessage = messages::show }
    }
    LaunchedEffect(session) { session.run() }
    val state = session.state
    val current = state.settings
    val logPolicy = state.confirmedSettings?.logging
    LaunchedEffect(logPolicy, state.readFailed) {
        // 只应用已落盘配置；读取失败时显示错误，不提前按默认限额清理历史。
        if (logPolicy != null) {
            try {
                AppLog.configure(logPolicy)
                logger.info { "[日志] 已应用配置：单文件 ${logPolicy.fileMiB} MiB，总量 ${logPolicy.totalMiB} MiB，保留 ${logPolicy.retentionDays} 天，级别 ${logPolicy.level}" }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messages.show(com.ohmyfly.ui.AppMessageKind.Error, "日志配置应用失败，请在日志页面重试")
            }
        }
    }
    val retryRead: () -> Unit = if (repository == null) ({ retry += 1 }) else session::retryRead

    CompositionLocalProvider(LocalAppMessages provides messages) {
        content(
            current,
            { name, transform -> session.change(name, transform) },
            session,
            retryRead,
        )
    }
}
