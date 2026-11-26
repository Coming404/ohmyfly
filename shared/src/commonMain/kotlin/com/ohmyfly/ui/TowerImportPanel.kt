package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    // 添加面板按原型先展示三种来源入口，展开后填写，收起保留草稿。
    var expandedSources by remember(target?.id) { mutableStateOf(emptySet<String>()) }
    val focus = LocalFocusManager.current
    val messages = LocalAppMessages.current
    OverlayBottomSheet(show = true, cornerRadius = AppCorners.Sheet, insideMargin = catalogSheetMargin(),
        startAction = { Text(if (target == null) "添加塔台" else "重新导入", fontSize = 20.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
        onDismissRequest = { if (!draft.reading) onClose() }, backgroundColor = MiuixTheme.colorScheme.background,
        endAction = { IconButton(onClick = hapticClick(onClose), enabled = !draft.reading,
            modifier = Modifier.size(44.dp).background(MiuixTheme.colorScheme.surfaceContainer, AppCorners.Capsule), cornerRadius = AppCorners.Toolbar) {
            Icon(DriftIcons.Clear, "关闭", Modifier.size(22.dp), tint = appIconTint())
        } }) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalWindowInfo.current.containerDpSize.height * 0.76f)) {
            // 正文可滚动，提交区固定在抽屉底部；IME 高度只由 Miuix 处理。
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (target == null) TowerImportSources(draft, settings, expandedSources,
                    onExpanded = { value -> expandedSources = if (value in expandedSources) expandedSources - value else expandedSources + value })
                else TowerImportInput(draft, settings, draft.mode, replacing = true)
                TextField(value = draft.name, textStyle = catalogInputStyle(), onValueChange = { draft.name = it },
                    insideMargin = androidx.compose.ui.unit.DpSize(12.dp, 12.dp), singleLine = true,
                    label = "塔台名称（可选）", useLabelAsPlaceholder = true, cornerRadius = AppCorners.Input)
                if (target == null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("合并为一个塔台", Modifier.weight(1f), style = catalogInputStyle())
                    Switch(checked = draft.merge, onCheckedChange = { draft.merge = it }, enabled = !draft.reading)
                }
                Spacer(Modifier.height(4.dp))
            }
            HorizontalDivider()
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                draft.failure?.let { CatalogNotice(it) }
                if (draft.reading) LinearProgressIndicator()
                Button(onClick = hapticClick {
                    try {
                        onSubmit(draft.inputs(settings, target), draft.name.trim(), draft.merge && target == null, target?.id)
                        focus.clearFocus()
                        messages.show(AppMessageKind.Success,
                            if (target == null) "已添加塔台并开始同步" else "已加入重新导入队列")
                        onClose()
                    } catch (e: Exception) { draft.failure = e.message ?: "导入未开始，请检查来源" }
                }, modifier = Modifier.fillMaxWidth(), minHeight = 52.dp,
                    enabled = ready && !draft.reading && draft.hasInput, colors = ButtonDefaults.buttonColorsPrimary(), cornerRadius = AppCorners.Control) {
                    Icon(MirrorIcons.Add, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (target == null) "添加塔台" else "重新导入", autoSize = CatalogTextSizing.Button,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TowerImportSources(
    draft: TowerImportDraft, settings: SubscriptionSettings, expanded: Set<String>, onExpanded: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth(), cornerRadius = AppCorners.Card) {
        TowerImportSection("url", "订阅地址 · 协议链接 · YAML 正文", "多行粘贴 · 自动识别内容",
            MirrorIcons.Link, expanded, onExpanded) { TowerImportInput(draft, settings, "url", replacing = false) }
        HorizontalDivider()
        TowerImportSection("file", "Mihomo YAML 文件", ".yaml / .yml · 多选导入",
            SettingsIcons.File, expanded, onExpanded) { TowerImportInput(draft, settings, "file", replacing = false) }
        HorizontalDivider()
        TowerImportSection("crawl", "全网打野", "暂未开放", MirrorIcons.Globe, expanded, onExpanded) {
            Text("暂未开放", style = catalogInputStyle(), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

// 同组来源行独立展开；关闭某一行保留输入，不把整张来源卡分割成多张卡片。
@Composable
private fun TowerImportSection(
    key: String, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Set<String>, onExpanded: (String) -> Unit, content: @Composable () -> Unit,
) {
    val open = key in expanded
    Column(Modifier.fillMaxWidth()) {
        Button(onClick = hapticClick { onExpanded(key) }, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(color = Color.Transparent, disabledColor = Color.Transparent,
                contentColor = MiuixTheme.colorScheme.onSurface), cornerRadius = AppCorners.Control) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CatalogTile(icon, 38, color = when (key) {
                    "file" -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    "crawl" -> Color(0xFF237C45)
                    else -> MiuixTheme.colorScheme.primary
                })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(title, style = catalogInputStyle(), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(subtitle, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                Icon(MiuixIcons.Basic.ArrowRight, if (open) "收起$title" else "展开$title",
                    Modifier.size(18.dp).rotate(if (open) 90f else 0f),
                    tint = if (open) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        CatalogExpansion(open) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
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
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp), cornerRadius = AppCorners.Control) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(MirrorIcons.Add, null, Modifier.size(20.dp))
                    Text("选择 YAML 文件", autoSize = CatalogTextSizing.Body)
                }
            }
            if (draft.files.isEmpty()) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(SettingsIcons.File, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text("尚未选择文件", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            draft.files.forEachIndexed { index, file ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(SettingsIcons.File, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(file.name, Modifier.weight(1f), maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    IconButton(onClick = hapticClick { draft.files = draft.files.filterIndexed { i, _ -> i != index } },
                        modifier = Modifier.size(44.dp), enabled = !draft.reading, cornerRadius = AppCorners.Toolbar) {
                        Icon(MirrorIcons.Trash, "移除 ${file.name}", tint = MiuixTheme.colorScheme.error)
                    }
                }
            }
        }
        else -> {
            val value = if (mode == "url") draft.urls else draft.text
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (mode == "url") "订阅地址、协议链接或 YAML 正文" else "YAML 正文或节点链接",
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
                singleLine = false, minLines = 3, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), cornerRadius = AppCorners.Input)
            if (value.isNotEmpty()) Text("${value.lineSequence().count()} 行 · ${value.length} 字符",
                style = catalogInputStyle(), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
