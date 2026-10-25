package com.ohmyfly

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.settings.createSettingsRepository
import kotlinx.coroutines.flow.flow

/**
 * 设置读取与保存的唯一入口：点击经校验后由 [SettingsSession] 立即更新界面，
 * DataStore 事务在后台排队完成；向镜像页透传同一个会话以等待真实写入确认。
 */
@Composable
internal fun PersistedSettings(
    path: String,
    content: @Composable (AppSettings, ((AppSettings) -> AppSettings) -> Unit, String?, () -> Unit, SettingsSession, () -> Unit) -> Unit,
) {
    var retry by remember { mutableStateOf(0) }
    val repository = remember(path, retry) { runCatching { createSettingsRepository(path) }.getOrNull() }
    val session = remember(path, repository, retry) {
        repository?.let { repo -> SettingsSession(repo.data, repo::update) }
            ?: SettingsSession(flow { error("设置存储不可用") }) { error("设置存储不可用") }
    }
    LaunchedEffect(session) { session.run() }
    val state = session.state
    val current = state.settings
    val retryRead: () -> Unit = if (repository == null) ({ retry += 1 }) else session::retryRead

    content(
        current,
        { transform -> session.change(transform) },
        state.error,
        session::dismissError,
        session,
        retryRead,
    )
}
