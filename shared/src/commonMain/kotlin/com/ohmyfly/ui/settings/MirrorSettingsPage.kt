package com.ohmyfly.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.mirror.sortMirrors
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.ui.LocalAppSurfaceColors
import com.ohmyfly.ui.hapticClick
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MirrorSettingsPage(
    settings: AppSettings,
    onChange: (String, (AppSettings) -> AppSettings) -> Unit,
    contentPadding: PaddingValues,
    onEditorBackChange: ((() -> Unit)?) -> Unit,
    session: SettingsSession,
    runner: MirrorProbeRunner,
    onAddActionChange: ((() -> Unit)?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val page = remember(session, scope) { MirrorPageState(session, scope) }
    val listState = rememberLazyListState()
    val confirmed = session.state.confirmedSettings ?: settings
    val mirrors = confirmed.mirrors
    val selectedId = settings.selectedMirrorId?.takeIf { id -> mirrors.any { it.id == id } } ?: mirrors.firstOrNull()?.id
    val focus = LocalFocusManager.current
    val menuFocus = remember { mutableMapOf<String, FocusRequester>() }
    var returnFocusId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(page.present) {
        if (!page.present && returnFocusId != null) {
            androidx.compose.runtime.withFrameNanos { }
            menuFocus[returnFocusId]?.requestFocus()
        }
    }
    val close: () -> Unit = { focus.clearFocus(); page.dismiss() }
    val reportBack by rememberUpdatedState(onEditorBackChange)
    val reportAdd by rememberUpdatedState(onAddActionChange)
    DisposableEffect(page) {
        reportAdd { page.show(MirrorPageState.Panel.Edit(page.draft(null))) }
        onDispose { reportAdd(null); reportBack(null) }
    }
    LaunchedEffect(page.present, page.visible) {
        reportBack(if (page.present) close else null)
    }
    LaunchedEffect(session.state.readFailed, session.state.confirmedSettings) { page.resolveRead() }

    LaunchedEffect(mirrors) { runner.syncMirrors(mirrors) }
    val ordered = sortMirrors(mirrors, runner.results)
    LaunchedEffect(runner.completedBatchId, mirrors) {
        val fastest = runner.consumeFastestMirrorId()?.takeIf { id -> mirrors.any { it.id == id } }
        if (fastest != null && settings.selectedMirrorId != fastest) {
            onChange("镜像选择") { current ->
                if (current.selectedMirrorId == fastest) current else current.copy(selectedMirrorId = fastest)
            }
        }
    }

    var frozen by remember { mutableStateOf<List<MirrorEntry>?>(null) }
    val interacting = page.present || listState.isScrollInProgress
    LaunchedEffect(interacting) { frozen = if (interacting) ordered else null }
    val shown = (frozen ?: ordered).mapNotNull { item -> mirrors.firstOrNull { it.id == item.id } }
    val direction = LocalLayoutDirection.current
    val padding = PaddingValues(
        start = contentPadding.calculateStartPadding(direction),
        end = contentPadding.calculateEndPadding(direction),
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding() + 80.dp,
    )
    val unfinished = page.drafts.values.filter { it.unfinished }
    Box(Modifier.fillMaxSize()) {
        SettingsList(padding, listState) {
            item(key = "mirror-title") {
                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(MirrorIcons.Github, null, Modifier.size(24.dp))
                    Text("GitHub 镜像", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
                }
            }
            item(key = "mirror-enabled") {
                SettingsCard {
                    ToggleSetting("启用镜像", value = settings.mirrorEnabled,
                        onChange = { enabled -> onChange("启用镜像") { it.copy(mirrorEnabled = enabled) } },
                        leadingIcon = MirrorIcons.Power,
                        enabled = !session.state.readFailed)
                }
            }
            if (unfinished.isNotEmpty()) item(key = "mirror-drafts") {
                Box(Modifier.fillMaxWidth()) {
                    SettingsCard {
                        unfinished.forEach { draft -> key(draft.id) {
                            TextButton(
                                text = "${draft.name.text.ifBlank { "新增镜像" }} · 草稿",
                                enabled = settings.mirrorEnabled && !session.state.readFailed,
                                onClick = hapticClick { page.show(MirrorPageState.Panel.Edit(draft)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } }
                    }
                    MirrorDisabledVeil(!settings.mirrorEnabled || session.state.readFailed)
                }
            }
            if (mirrors.isEmpty()) item(key = "mirror-empty") {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(vertical = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(MirrorIcons.Github, contentDescription = null, modifier = Modifier.size(48.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text("暂无镜像源", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
                }
            }
            if (mirrors.isNotEmpty()) item(key = "mirror-sources") {
                Box(Modifier.fillMaxWidth()) {
                    SettingsCard {
                        MirrorSourceHeader()
                        shown.forEach { entry -> key(entry.id) {
                            SourceDivider()
                            MirrorRow(
                                name = entry.name, address = hostOf(entry.address), selected = entry.id == selectedId,
                                selectionEnabled = settings.mirrorEnabled && !session.state.readFailed,
                                result = runner.result(entry.id), phase = runner.phase(entry.id),
                                onSelect = { if (settings.selectedMirrorId != entry.id) onChange("镜像选择") { it.copy(selectedMirrorId = entry.id) } },
                                testing = runner.running || runner.stopping,
                                canWrite = !session.state.readFailed,
                                onTest = { runner.measure(entry.id, entry) },
                                onEdit = { returnFocusId = entry.id; page.show(MirrorPageState.Panel.Edit(page.draft(entry))) },
                                onDelete = { returnFocusId = entry.id; page.show(MirrorPageState.Panel.Delete(entry.id)) },
                                menuFocusRequester = menuFocus.getOrPut(entry.id) { FocusRequester() },
                            )
                        } }
                    }
                    MirrorDisabledVeil(!settings.mirrorEnabled || session.state.readFailed)
                }
            }
        }
        if (mirrors.isNotEmpty()) MirrorProbeBar(
            runner, mirrors, enabled = settings.mirrorEnabled && !session.state.readFailed,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(LocalAppSurfaceColors.current.page)
                .padding(bottom = contentPadding.calculateBottomPadding(),
                    start = contentPadding.calculateStartPadding(direction) + 16.dp,
                    end = contentPadding.calculateEndPadding(direction) + 16.dp, top = 12.dp),
        )
    }
    val panel = page.panel
    val editor = (panel as? MirrorPageState.Panel.Edit)?.draft
    MirrorDialog(
        show = page.visible,
        title = when (panel) {
            is MirrorPageState.Panel.Edit -> if (panel.draft.confirmed == null) "添加镜像" else "编辑镜像"
            is MirrorPageState.Panel.Delete -> "删除${mirrors.firstOrNull { it.id == panel.id }?.name ?: "镜像"}？"
            null -> ""
        },
        icon = if (panel is MirrorPageState.Panel.Delete) MirrorIcons.Trash else MirrorIcons.Github,
        onDismiss = close,
        onDismissFinished = { page.finishDismiss() },
        footer = {
            if (editor != null) MirrorEditorSave(editor, session.state.readFailed,
                onSave = { page.submit(editor) }, onRetryRead = { session.retryRead() })
        },
    ) {
        when (panel) {
            is MirrorPageState.Panel.Edit -> MirrorEditor(panel.draft, settings.mirrors, session.state.readFailed)
            is MirrorPageState.Panel.Delete -> MirrorDeleteContent(
                current = panel.id == selectedId, lastMirror = mirrors.size == 1, busy = page.deleting,
                error = page.deleteError, readFailed = session.state.readFailed,
                onKeep = close, onDelete = {
                    val index = shown.indexOfFirst { it.id == panel.id }
                    returnFocusId = shown.getOrNull(index + 1)?.id ?: shown.getOrNull(index - 1)?.id
                    page.delete(panel.id)
                }, onRetryRead = { session.retryRead() })
            null -> Unit
        }
    }
}

@Composable
private fun SourceDivider() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(LocalAppSurfaceColors.current.cardOutline))
}
