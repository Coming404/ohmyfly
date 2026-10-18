package dev.ohmydrift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.settings.AppSettings
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CurrentMirrorCard(settings: AppSettings, selectedId: String?) {
    val custom = settings.mirrors.firstOrNull { it.id == selectedId }
    Card(Modifier.fillMaxWidth()) {
        BasicComponent(title = "当前使用", summary = custom?.let { "${it.name} · ${it.address}" } ?: "直连")
        MirrorRouteCheck(settings)
    }
}

@Composable
internal fun ProbeActions(runner: MirrorProbeRunner, mirrors: List<MirrorEntry>, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (runner.running) {
            TextButton(text = "停止测试", onClick = { runner.stop() }, modifier = Modifier.weight(1f))
            Text("已完成 ${runner.completed} / ${runner.total}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } else {
            TextButton(text = "测延迟", onClick = {
                runner.measureAll(listOf(DIRECT_MIRROR_KEY to null) + mirrors.map { it.id to it })
            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
        }
        TextButton(text = "＋添加", onClick = onAdd)
    }
    if (!runner.running) {
        val failed = (listOf(DIRECT_MIRROR_KEY to null) + mirrors.map { it.id to it }).filter { (id, _) ->
            runner.results[id]?.status?.let { it != dev.ohmydrift.mirror.ProbeStatus.Success } == true
        }
        if (failed.isNotEmpty()) TextButton(text = "重试失败项（${failed.size}）", onClick = { runner.measureAll(failed) })
    }
}
