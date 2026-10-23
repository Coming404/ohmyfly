package com.ohmyfly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.settings.SettingsUiState
import com.ohmyfly.settings.createSettingsRepository
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置读取与保存的唯一入口：点击经校验后由 [SettingsSession] 立即更新界面，
 * DataStore 事务在后台排队完成；向镜像页透传同一个会话以等待真实写入确认。
 */
@Composable
internal fun PersistedSettings(
    path: String,
    content: @Composable (AppSettings, ((AppSettings) -> AppSettings) -> Unit, String?, () -> Unit, SettingsSession) -> Unit,
) {
    var retry by remember { mutableStateOf(0) }
    val repository = remember(path, retry) { runCatching { createSettingsRepository(path) }.getOrNull() }
    val session = remember(path, repository, retry) {
        repository?.let { repo -> SettingsSession(repo.data, repo::update) }
    }
    LaunchedEffect(session) { session?.run() }
    val state = session?.state ?: SettingsUiState(readFailed = true)
    val current = state.settings
    if (current == null) {
        MiuixTheme {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("OhMyFly", style = MiuixTheme.textStyles.title2)
                Text(if (state.readFailed) "无法读取本地设置，原文件未被覆盖。" else "正在读取本地设置…")
                if (state.readFailed) TextButton(text = "重试", onClick = { retry++ })
            }
        }
    } else {
        content(
            current,
            { transform -> checkNotNull(session).change(transform) },
            state.error,
            { checkNotNull(session).dismissError() },
            checkNotNull(session),
        )
    }
}
