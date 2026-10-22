package dev.ohmydrift.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ohmydrift.mirror.MirrorEntry
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MirrorManageContent(
    id: String,
    mirrors: List<MirrorEntry>,
    runner: MirrorProbeRunner,
    onEdit: (MirrorEntry) -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onTest: () -> Unit,
    canWrite: Boolean,
) {
    val entry = mirrors.firstOrNull { it.id == id }
    val index = mirrors.indexOfFirst { it.id == id }
    MirrorDialogAction("测试", MirrorIcons.Play, enabled = !runner.running && !runner.stopping, onClick = onTest)
    if (entry != null) {
        MirrorDialogAction("编辑", MirrorIcons.Edit, enabled = canWrite, onClick = { onEdit(entry) })
        MirrorDialogAction("上移", MirrorIcons.Up,
            enabled = canWrite && index > 0,
            onClick = { onMove(-1) })
        MirrorDialogAction("下移", MirrorIcons.Down,
            enabled = canWrite && index in 0 until mirrors.lastIndex,
            onClick = { onMove(1) })
        MirrorDialogAction("删除", MirrorIcons.Trash, enabled = canWrite, danger = true, onClick = onDelete)
    }
}

@Composable
internal fun MirrorDeleteContent(
    current: Boolean,
    lastMirror: Boolean,
    busy: Boolean,
    error: String?,
    readFailed: Boolean,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    onRetryRead: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(when {
            lastMirror -> "删除后将关闭镜像功能。"
            current -> "删除后改用列表中的首个镜像。"
            else -> "删除后将从镜像列表移除。"
        }, fontSize = 14.sp)
        if (error != null) MirrorEditorFeedback(error)
        if (readFailed) {
            TextButton(text = "重试读取", onClick = onRetryRead, modifier = Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(shape)
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onKeep).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MirrorIcons.Shield, null, Modifier.size(20.dp))
                Text("保留", fontSize = 15.sp)
            }
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(shape)
                    .background(MiuixTheme.colorScheme.error)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onDelete).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MirrorIcons.Trash, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.onError)
                Text(if (busy) "删除中…" else "删除", fontSize = 15.sp, color = MiuixTheme.colorScheme.onError)
            }
        }
    }
}
