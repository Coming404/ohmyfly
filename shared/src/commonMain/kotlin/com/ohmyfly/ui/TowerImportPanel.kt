package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.CatalogTower
import com.ohmyfly.settings.SubscriptionSettings
import com.ohmyfly.subscription.*
import com.ohmyfly.ui.settings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

@Composable
internal fun TowerImportPanel(
    ready: Boolean, settings: SubscriptionSettings, target: CatalogTower?,
    onSubmit: (List<com.ohmyfly.catalog.CatalogImportSource>, String, Boolean, Long?) -> Unit, onClose: () -> Unit,
) {
    val draft = remember(target?.id) { TowerImportDraft(target) }
    val focus = LocalFocusManager.current
    var accepted by remember(target?.id) { mutableStateOf(false) }
    OverlayBottomSheet(show = true, insideMargin = catalogSheetMargin(), title = if (target == null) "添加塔台" else "重新导入",
        onDismissRequest = { if (!draft.reading) onClose() }, backgroundColor = MiuixTheme.colorScheme.background,
        endAction = { IconButton(onClick = hapticClick(onClose), enabled = !draft.reading) { Icon(DriftIcons.Clear, "关闭") } }) {
        // 入队成功留在抽屉内反馈，完成按钮关闭；隐藏表单避免重复提交同一份来源。
        if (accepted) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CatalogNotice("已加入导入队列", CatalogNoticeKind.Success)
                CatalogTextButton("完成", onClick = hapticClick(onClose), modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary())
            }
            return@OverlayBottomSheet
        }
        Column(Modifier.fillMaxWidth().heightIn(max = LocalWindowInfo.current.containerDpSize.height * 0.72f)
            .verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (target == null) TowerImportSources(draft.mode, !draft.reading) { draft.mode = it; draft.failure = null }
            TowerImportInput(draft, settings, target != null)
            Text("名称（自动命名）", style = catalogInputStyle())
            TextField(value = draft.name, textStyle = catalogInputStyle(), onValueChange = { draft.name = it },
                insideMargin = androidx.compose.ui.unit.DpSize(12.dp, 12.dp), singleLine = true)
            if (target == null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("合并为一个塔台", Modifier.weight(1f), style = catalogInputStyle())
                Switch(checked = draft.merge, onCheckedChange = { draft.merge = it }, enabled = !draft.reading)
            }
            draft.failure?.let { CatalogNotice(it) }
            if (draft.reading) LinearProgressIndicator()
            Button(onClick = hapticClick {
                try {
                    onSubmit(draft.inputs(settings, target), draft.name.trim(), draft.merge && target == null, target?.id)
                    accepted = true
                    focus.clearFocus()
                } catch (e: Exception) {
                    draft.failure = e.message ?: "导入未开始，请检查来源"
                }
            }, modifier = Modifier.fillMaxWidth(), minHeight = 52.dp,
                enabled = ready && !draft.reading && draft.hasInput, colors = ButtonDefaults.buttonColorsPrimary()) {
                Text(if (target == null) "添加塔台" else "重新导入", autoSize = CatalogTextSizing.Button,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TowerImportInput(draft: TowerImportDraft, settings: SubscriptionSettings, replacing: Boolean) {
    val picker = LocalSubscriptionFilePicker.current
    val scope = rememberCoroutineScope()
    if (draft.mode == "file") Card(Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        Button(onClick = hapticClick { scope.launch {
            draft.reading = true; draft.failure = null
            try {
                val files = picker.pick(if (replacing) 1 else settings.fileLimit)
                if (files.isNotEmpty()) draft.files = files
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                draft.failure = e.message ?: "文件选择失败，请检查权限"
            } finally { draft.reading = false }
        } }, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp), enabled = !draft.reading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(SettingsIcons.File, null, Modifier.size(28.dp))
                Text(if (draft.files.isEmpty()) "选择配置文件" else "已选择 ${draft.files.size} 个文件",
                    autoSize = CatalogTextSizing.Body)
                draft.files.take(3).forEach { Text(it.name, autoSize = CatalogTextSizing.Notice) }
            }
        }
        if (draft.files.isNotEmpty()) CatalogAction("清除选择", DriftIcons.Clear) { draft.files = emptyList() }
    } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (draft.mode == "url") "订阅地址或节点链接，每行一个" else "YAML 正文或节点链接", style = catalogInputStyle())
        TextField(value = if (draft.mode == "url") draft.urls else draft.text,
            onValueChange = { if (draft.mode == "url") draft.urls = it else draft.text = it; draft.failure = null },
            textStyle = catalogInputStyle(), insideMargin = androidx.compose.ui.unit.DpSize(12.dp, 12.dp),
            singleLine = false, minLines = 3, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp))
    }
}

@Composable
private fun TowerImportSources(mode: String, enabled: Boolean, onMode: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("url" to "链接", "text" to "文本", "file" to "文件").forEach { (value, label) ->
            Button(onClick = hapticClick { onMode(value) },
                modifier = Modifier.weight(1f).semantics { selected = mode == value }, enabled = enabled,
                colors = if (mode == value) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (value == "url") MirrorIcons.Link else SettingsIcons.File, null, Modifier.size(22.dp))
                    Text(label, autoSize = CatalogTextSizing.Caption, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}
