package dev.ohmydrift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ohmydrift.mirror.MirrorProbeTarget
import dev.ohmydrift.mirror.MirrorResourceFetcher
import dev.ohmydrift.mirror.createMirrorTransport
import dev.ohmydrift.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Exercises the same manual-priority chain used for public GitHub resource requests. */
@Composable
internal fun MirrorRouteCheck(settings: AppSettings) {
    val scope = rememberCoroutineScope()
    val fetcher = remember { MirrorResourceFetcher(createMirrorTransport()) }
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var generation by remember { mutableStateOf(0) }
    LaunchedEffect(settings.selectedMirrorId, settings.mirrors) {
        generation++
        job?.cancel()
        running = false
        message = null
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("手动选择优先，失败后按手动列表顺序尝试；不因排序更换当前选择。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(text = if (running) "停止验证" else "验证当前线路（含降级）", onClick = {
                if (running) {
                    generation++
                    job?.cancel()
                    running = false
                    message = "已停止验证"
                } else {
                    val run = ++generation
                    val snapshot = settings
                    running = true
                    message = null
                    job = scope.launch {
                        try {
                            val result = fetcher.fetch(
                                originalUrl = MirrorProbeTarget.url,
                                selectedMirrorId = snapshot.selectedMirrorId,
                                mirrors = snapshot.mirrors,
                                expectedSha256 = MirrorProbeTarget.sha256,
                            )
                            if (run == generation) {
                                val name = result.usedMirrorId?.let { id -> snapshot.mirrors.firstOrNull { it.id == id }?.name } ?: "直连"
                                message = "内容校验通过 · 实际使用：$name · 降级 ${result.failedMirrorIds.size} 次"
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            if (run == generation) message = "线路均请求失败，请逐项测延迟查看状态。"
                        } finally {
                            if (run == generation) running = false
                        }
                    }
                }
            })
        }
        message?.let { Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
    }
}
