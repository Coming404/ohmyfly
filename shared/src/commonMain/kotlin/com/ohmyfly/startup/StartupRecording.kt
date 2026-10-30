package com.ohmyfly.startup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.ui.AppMessageKind
import com.ohmyfly.ui.LocalAppMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

@Composable
internal fun StartupFrameRecording(record: StartupRecord) {
    LaunchedEffect(record) {
        val remaining = record.remainingMillis()
        if (remaining > 0) withTimeoutOrNull(remaining) {
            val sample = record::sampleFrame
            while (record.recording) withFrameNanos(sample)
        }
    }
}

@Composable
internal fun StartupSettingsRecording(record: StartupRecord, session: SettingsSession, settings: AppSettings, dark: Boolean) {
    SideEffect { record.appearance(settings, dark) }
    LaunchedEffect(record, session) {
        val remaining = record.remainingMillis()
        if (remaining > 0) withTimeoutOrNull(remaining) {
            val result = snapshotFlow { session.state }.first { it.confirmedSettings != null || it.readFailed }
            record.settingsRead(succeeded = !result.readFailed)
        }
    }
}

@Composable
internal fun Modifier.startupFirstDraw(): Modifier {
    val record = remember { StartupRecords.current }
    return drawWithContent {
        drawContent()
        record.firstDraw()
    }
}

@Composable
internal expect fun rememberStartupShare(): (String) -> Unit

@Composable
internal fun rememberExportStartupRecords(): () -> Unit {
    val share = rememberStartupShare()
    val scope = rememberCoroutineScope()
    val messages = LocalAppMessages.current
    return {
        scope.launch {
            try {
                val path = StartupRecords.current.device.path
                val exists = withContext(Dispatchers.IO) { FileSystem.SYSTEM.exists(path.toPath()) }
                if (exists) {
                    share(path)
                } else {
                    val error = if (StartupRecords.writeFailed) "启动记录写入失败" else "启动记录文件不存在"
                    messages.show(AppMessageKind.Error, error)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messages.show(AppMessageKind.Error, "无法导出启动记录")
            }
        }
    }
}
