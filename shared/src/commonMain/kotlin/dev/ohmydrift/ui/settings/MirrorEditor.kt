package dev.ohmydrift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.MirrorMode
import dev.ohmydrift.mirror.newMirrorId
import dev.ohmydrift.mirror.validateMirror
import dev.ohmydrift.ui.ChoiceOption
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Fixed sample; it only illustrates how the address is rewritten, nothing is fetched. */
private const val SAMPLE_URL = "https://github.com/owner/repo/archive/main.zip"

@Composable
internal fun MirrorEditor(
    existing: MirrorEntry?,
    siblings: List<MirrorEntry>,
    onCancel: () -> Unit,
    onSubmit: (MirrorEntry) -> Unit,
    contentPadding: PaddingValues,
) {
    val draft = remember(existing?.id) { MirrorDraft(existing) }
    val name = draft.name.text.toString()
    val address = draft.address.text.toString()
    val cleaned = address.trim()
    val id = remember(existing?.id) { existing?.id ?: newMirrorId(siblings.map { it.id }.toSet()) }
    val entry = MirrorEntry(
        id = id,
        name = name.trim().ifBlank { hostOf(cleaned) },
        address = cleaned,
        mode = draft.mode,
    )
    // Empty address is "not filled yet", not an error; validation starts once there is input.
    val error = if (cleaned.isBlank()) null else (validateMirror(entry) ?: duplicateError(entry, siblings))
    SettingsList(contentPadding) {
        item { SmallTitle(if (existing == null) "添加镜像" else "编辑镜像") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        state = draft.name,
                        label = "名称（留空使用域名）",
                        useLabelAsPlaceholder = true,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("类型", style = MiuixTheme.textStyles.title4)
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MirrorMode.entries.forEach { mode ->
                            ChoiceOption(
                                label = mode.editorLabel,
                                selected = draft.mode == mode,
                                onClick = { draft.mode = mode },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    TextField(
                        state = draft.address,
                        label = "HTTPS 地址",
                        useLabelAsPlaceholder = true,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            autoCorrectEnabled = false,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error != null) {
                        Text(error, color = MiuixTheme.colorScheme.error)
                    } else if (cleaned.isBlank()) {
                        Text("填写 HTTPS 地址后可保存。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    Text("转换示例", style = MiuixTheme.textStyles.title4)
                    Text(
                        "$SAMPLE_URL\n→ ${mirrorUrlPreview(entry, SAMPLE_URL)}",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        if (draft.mode == MirrorMode.Template) "模板必须恰好包含一个 {url}，替换时对原地址做百分号编码。"
                        else "原始地址原样接在前缀后；基址不接受查询参数。",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "保存",
                    enabled = error == null && cleaned.isNotBlank(),
                    onClick = { onSubmit(entry) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                TextButton(text = "取消", onClick = onCancel, modifier = Modifier.weight(1f))
            }
        }
        item {
            BasicComponent(
                title = "候选未经测试",
                summary = "保存不会联网；探测只验证固定样本，不代表运营方可信。",
            )
        }
    }
}

/** Name is not part of identity; duplicates are decided by address and mode. */
internal fun duplicateError(entry: MirrorEntry, siblings: List<MirrorEntry>): String? =
    siblings.firstOrNull {
        it.id != entry.id && it.mode == entry.mode && canonicalAddress(it) == canonicalAddress(entry)
    }?.let { "与「${it.name}」的地址重复" }

/** UI deduplication and persistence use the same URL normalization contract. */
internal fun canonicalAddress(entry: MirrorEntry): String = dev.ohmydrift.mirror.normalizedMirrorAddress(entry)

internal fun hostOf(address: String): String =
    address.trim().removePrefix("https://").substringBefore('/').substringBefore('?').ifBlank { "新镜像" }

private class MirrorDraft(existing: MirrorEntry?) {
    val name = TextFieldState(existing?.name.orEmpty())
    val address = TextFieldState(existing?.address.orEmpty())
    var mode by mutableStateOf(existing?.mode ?: MirrorMode.Prefix)
}

private val MirrorMode.editorLabel: String
    get() = when (this) {
        MirrorMode.Prefix -> "前缀代理"
        MirrorMode.Template -> "编码网址模板"
    }
