package dev.ohmydrift

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ohmydrift.settings.AppSettings
import dev.ohmydrift.settings.createSettingsRepository
import dev.ohmydrift.settings.validateSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PersistedSettings(
    path: String,
    content: @Composable (AppSettings, ((AppSettings) -> AppSettings) -> Unit, String?, () -> Unit) -> Unit,
) {
    var retry by remember { mutableStateOf(0) }
    val repository = remember(path, retry) { runCatching { createSettingsRepository(path) }.getOrNull() }
    var settings by remember(path) { mutableStateOf<AppSettings?>(null) }
    var readFailed by remember { mutableStateOf(false) }
    var writeError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository, retry) {
        readFailed = repository == null
        if (repository != null) {
            try {
                repository.data.collect { settings = it; readFailed = false }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed read must never overwrite the stored file with defaults.
                readFailed = true
            }
        }
    }
    val current = settings
    if (current == null || readFailed) {
        MiuixTheme {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("ohmydrift", style = MiuixTheme.textStyles.title2)
                Text(if (readFailed) "无法读取本地设置，原文件未被覆盖。" else "正在读取本地设置…")
                if (readFailed) TextButton(text = "重试", onClick = { retry++ })
            }
        }
    } else {
        content(current, { transform ->
            val validation = validateSettings(transform(current))
            if (validation != null) writeError = validation
            else scope.launch {
                try {
                    checkNotNull(repository).update(transform)
                    writeError = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    writeError = "请检查输入或设备存储后重试。更改未保存。"
                }
            }
        }, writeError, { writeError = null })
    }
}
