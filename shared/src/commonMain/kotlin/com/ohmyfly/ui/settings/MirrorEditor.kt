package com.ohmyfly.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.mirror.MirrorProbeTarget
import com.ohmyfly.ui.hapticClick
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults

@Composable
internal fun MirrorEditor(
    draft: MirrorDraftState,
    siblings: List<MirrorEntry>,
    readFailed: Boolean,
) {
    val focus = LocalFocusManager.current
    val editable = draft.saving == 0 && !readFailed
    val entry = draft.candidate
    val validation = if (draft.attempted) mirrorDraftError(entry, siblings) else null
    val inputColors = TextFieldDefaults.textFieldColors()
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EditorLabel("名称", MirrorIcons.Name)
        SettingsInputMenu {
            TextField(
                value = draft.name,
                onValueChange = { draft.name = it },
                enabled = editable,
                colors = inputColors,
                singleLine = true,
                insideMargin = androidx.compose.ui.unit.DpSize(12.dp, 12.dp),
                textStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.main.copy(fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { contentDescription = "名称" },
            )
        }
        MirrorPrefixRow()
        EditorLabel("地址", MirrorIcons.Globe)
        SettingsInputMenu {
            TextField(
                value = draft.address,
                onValueChange = { draft.address = it },
                enabled = editable,
                colors = inputColors,
                singleLine = true,
                insideMargin = androidx.compose.ui.unit.DpSize(12.dp, 12.dp),
                textStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.main.copy(fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { contentDescription = "地址"; validation?.let { error(it) } },
            )
        }
        if (validation != null) {
            MirrorEditorFeedback(validation)
        } else {
            EditorLabel("改写预览", MirrorIcons.External)
            SelectionContainer {
                Text(
                    if (entry.address.isBlank() || mirrorDraftError(entry, siblings) != null) "" else mirrorUrlPreview(entry, MirrorProbeTarget.url),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                        .background(inputColors.backgroundColor, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }
        }
        draft.failure?.let { MirrorEditorFeedback(it) }
    }
}

@Composable
internal fun MirrorEditorSave(
    draft: MirrorDraftState,
    readFailed: Boolean,
    onSave: () -> Unit,
    onRetryRead: () -> Unit,
) {
    val focus = LocalFocusManager.current
    TextButton(
        text = when { readFailed -> "重试读取"; draft.saving > 0 -> "保存中…"; else -> "保存" },
        onClick = hapticClick {
            focus.clearFocus()
            if (readFailed) onRetryRead() else onSave()
        },
        enabled = draft.saving == 0 && !draft.composing,
        colors = ButtonDefaults.textButtonColorsPrimary(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 48.dp),
    )
}

@Composable
private fun MirrorPrefixRow() {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .clearAndSetSemantics { contentDescription = "拼接方式，HTTP(S) 前缀" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorLabel("拼接方式", MirrorIcons.Link)
        Text(
            "HTTP(S) 前缀",
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EditorLabel(text: String, icon: ImageVector) {
    val iconColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Normal, color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun MirrorEditorFeedback(text: String) {
    Row(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
            .background(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MirrorIcons.Alert, null, Modifier.size(20.dp))
        Text(text, fontSize = 12.sp, color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.error)
    }
}
