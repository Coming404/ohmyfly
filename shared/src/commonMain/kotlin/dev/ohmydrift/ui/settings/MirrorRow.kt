package dev.ohmydrift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.MirrorProbeResult
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Row actions are grouped so the row itself stays a plain, stateless composable. */
internal class MirrorRowActions(
    val select: () -> Unit,
    val test: () -> Unit,
    val menu: () -> Unit,
    val edit: () -> Unit,
    val moveUp: () -> Unit,
    val moveDown: () -> Unit,
    val delete: () -> Unit,
)

@Composable
internal fun MirrorDirectRow(
    selected: Boolean,
    result: MirrorProbeResult?,
    phase: MirrorTestPhase,
    testing: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.clearAndSetSemantics {})
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("直连", style = MiuixTheme.textStyles.title4)
            Text("直接获取公开 GitHub 样本，不经镜像。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            MirrorStatus(result, phase)
        }
        TextButton(text = "测此项", enabled = !testing, onClick = onTest)
    }
}

@Composable
internal fun MirrorEntryRow(
    entry: MirrorEntry,
    selected: Boolean,
    result: MirrorProbeResult?,
    phase: MirrorTestPhase,
    menuOpen: Boolean,
    manualOrder: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    testing: Boolean,
    actions: MirrorRowActions,
) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = actions.select)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.clearAndSetSemantics {})
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.name, style = MiuixTheme.textStyles.title4)
            Text(entry.address, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            MirrorStatus(result, phase)
        }
        TextButton(text = "测此项", enabled = !testing, onClick = actions.test)
        TextButton(text = if (menuOpen) "收起" else "管理", onClick = actions.menu)
    }
    if (!menuOpen) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (!manualOrder) {
            Text(
                "切换为手动排序后可上下移动。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(text = "编辑", onClick = actions.edit)
            TextButton(text = "上移", enabled = manualOrder && canMoveUp, onClick = actions.moveUp)
            TextButton(text = "下移", enabled = manualOrder && canMoveDown, onClick = actions.moveDown)
            TextButton(
                text = "删除",
                onClick = actions.delete,
                colors = ButtonDefaults.textButtonColors(
                    textColor = MiuixTheme.colorScheme.error,
                ),
            )
        }
    }
}

@Composable
internal fun MirrorStatus(result: MirrorProbeResult?, phase: MirrorTestPhase) {
    val tone = mirrorTone(result, phase)
    Text(
        text = mirrorStatusText(result, phase),
        color = when (tone) {
            MirrorTone.Error -> MiuixTheme.colorScheme.error
            MirrorTone.Active -> MiuixTheme.colorScheme.primary
            MirrorTone.Normal -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        },
        style = MiuixTheme.textStyles.footnote1,
    )
}
