package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

@Composable
internal fun TowerImportPanel(
    ready: Boolean, settings: SubscriptionSettings, target: CatalogTower?,
    onSubmit: (List<com.ohmyfly.catalog.CatalogImportSource>, String, Boolean, Long?) -> kotlinx.coroutines.Deferred<Unit>, onClose: () -> Unit,
) {
    val draft = remember(target?.id) { TowerImportDraft(target) }
    var expandedSources by remember(target?.id) { mutableStateOf(setOf(draft.mode)) }
    val focus = LocalFocusManager.current
    val messages = LocalAppMessages.current
    OverlayBottomSheet(show = true, insideMargin = catalogSheetMargin(), title = if (target == null) "添加塔台" else "重新导入",
        onDismissRequest = { if (!draft.reading) onClose() }, backgroundColor = MiuixTheme.colorScheme.background,
        endAction = { IconButton(onClick = hapticClick(onClose), enabled = !draft.reading) { Icon(DriftIcons.Clear, "关闭") } }) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalWindowInfo.current.containerDpSize.height * 0.72f)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (target == null) TowerImportSources(draft, settings, expandedSources,
                onExpanded = { value -> expandedSources = if (value in expandedSources) expandedSources - value else expandedSources + value })
            else TowerImportInput(draft, settings, draft.mode, replacing = true)
            Text("塔台名称", style = catalogInputStyle())
            TextField(value = draft.name, textStyle = catalogInputStyle(), onValueChange = { draft.name = it },
                insideMargin = androidx.compose.ui.unit.DpSize(12.dp, 12.dp), singleLine = true,
                label = "自动命名", useLabelAsPlaceholder = true)
            if (target == null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("合并为一个塔台", Modifier.weight(1f), style = catalogInputStyle())
                Switch(checked = draft.merge, onCheckedChange = { draft.merge = it }, enabled = !draft.reading)
            }
            draft.failure?.let { CatalogNotice(it) }
            if (draft.reading) LinearProgressIndicator()
            Button(onClick = hapticClick {
                try {
                    onSubmit(draft.inputs(settings, target), draft.name.trim(), draft.merge && target == null, target?.id)
                    focus.clearFocus()
                    messages.show(AppMessageKind.Success,
                        if (target == null) "已添加塔台并开始同步" else "已加入重新导入队列")
                    onClose()
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
private fun TowerImportSources(
    draft: TowerImportDraft,
    settings: SubscriptionSettings,
    expanded: Set<String>,
    onExpanded: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TowerImportSection("url", "链接", MirrorIcons.Link, expanded, onExpanded) {
            TowerImportInput(draft, settings, "url", replacing = false)
        }
        TowerImportSection("text", "文本", SettingsIcons.File, expanded, onExpanded) {
            TowerImportInput(draft, settings, "text", replacing = false)
        }
        TowerImportSection("file", "文件", SettingsIcons.File, expanded, onExpanded) {
            TowerImportInput(draft, settings, "file", replacing = false)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(SettingsIcons.Search, "全网抓取", Modifier.size(22.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text("全网抓取", modifier = Modifier.weight(1f), style = catalogInputStyle(),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text("暂未开放", style = catalogInputStyle(), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun TowerImportSection(
    key: String,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Set<String>,
    onExpanded: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val open = key in expanded
    Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
        Column(Modifier.fillMaxWidth()) {
            Button(onClick = hapticClick { onExpanded(key) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(color = Color.Transparent, disabledColor = Color.Transparent,
                    contentColor = MiuixTheme.colorScheme.onSurface)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icon, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.primary)
                    Text(title, modifier = Modifier.weight(1f), style = catalogInputStyle())
                    Icon(MiuixIcons.Basic.ArrowRight, null, Modifier.size(18.dp).rotate(if (open) 90f else 0f),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            CatalogExpansion(open) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            }
        }
    }
}

@Composable
private fun TowerImportInput(draft: TowerImportDraft, settings: SubscriptionSettings, mode: String, replacing: Boolean) {
    val picker = LocalSubscriptionFilePicker.current
    val scope = rememberCoroutineScope()
    when (mode) {
        "file" -> {
            Button(onClick = hapticClick { scope.launch {
                draft.reading = true; draft.failure = null
                try {
                    val remaining = (if (replacing) 1 else settings.fileLimit) - draft.files.size
                    if (remaining > 0) draft.files = draft.files + picker.pick(remaining)
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    draft.failure = e.message ?: "文件选择失败"
                } finally { draft.reading = false }
            } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !draft.reading,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(SettingsIcons.File, null, Modifier.size(20.dp))
                    Text("选择 YAML 文件", autoSize = CatalogTextSizing.Body)
                }
            }
            draft.files.forEachIndexed { index, file ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(SettingsIcons.File, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(file.name, Modifier.weight(1f), maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    IconButton(onClick = hapticClick { draft.files = draft.files.filterIndexed { i, _ -> i != index } },
                        modifier = Modifier.size(44.dp), enabled = !draft.reading) {
                        Icon(MirrorIcons.Trash, "移除 ${file.name}", tint = MiuixTheme.colorScheme.error)
                    }
                }
            }
        }
        else -> {
            val value = if (mode == "url") draft.urls else draft.text
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (mode == "url") "订阅地址或节点链接" else "YAML 正文或节点链接",
                    modifier = Modifier.weight(1f), style = catalogInputStyle())
                CatalogTextButton("粘贴", onClick = hapticClick {
                    readCatalogText()?.let { pasted ->
                        if (mode == "url") draft.urls = listOf(draft.urls, pasted).filter(String::isNotBlank).joinToString("\n")
                        else draft.text = listOf(draft.text, pasted).filter(String::isNotBlank).joinToString("\n")
                    }
                })
            }
            TextField(value = value,
                onValueChange = { if (mode == "url") draft.urls = it else draft.text = it; draft.failure = null },
                textStyle = catalogInputStyle(), insideMargin = androidx.compose.ui.unit.DpSize(12.dp, 12.dp),
                singleLine = false, minLines = 3, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp))
            if (value.isNotEmpty()) Text("${value.lineSequence().count()} 行 · ${value.length} 字符",
                style = catalogInputStyle(), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
