package dev.ohmydrift.ui.settings

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
import androidx.compose.runtime.mutableStateMapOf
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
import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.MirrorOrder
import dev.ohmydrift.mirror.MirrorProbe
import dev.ohmydrift.mirror.createMirrorTransport
import dev.ohmydrift.mirror.sortMirrors
import dev.ohmydrift.settings.AppSettings
import dev.ohmydrift.settings.SettingsSession
import dev.ohmydrift.ui.LocalAppSurfaceColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MirrorSettingsPage(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    contentPadding: PaddingValues,
    onEditorBackChange: ((() -> Unit)?) -> Unit,
    session: SettingsSession,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    dark: Boolean,
    onAddActionChange: ((() -> Unit)?) -> Unit,
    onOverlayChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val runner = remember(scope) { MirrorProbeRunner(MirrorProbe(createMirrorTransport()), scope) }
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
    val reportOverlay by rememberUpdatedState(onOverlayChange)
    val reportAdd by rememberUpdatedState(onAddActionChange)
    DisposableEffect(page) {
        reportAdd { page.show(MirrorPageState.Panel.Edit(page.draft(null))) }
        onDispose { reportAdd(null); reportBack(null); reportOverlay(false); runner.stop() }
    }
    LaunchedEffect(page.present, page.visible) {
        reportOverlay(page.present)
        reportBack(if (page.present) close else null)
    }
    LaunchedEffect(session.state.readFailed, session.state.confirmedSettings) { page.resolveRead() }

    // Only confirmed target changes invalidate measurements; optimistic drafts never get probed.
    val signatures = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(mirrors) {
        val ids = mirrors.map { it.id }.toSet()
        (signatures.keys - ids).forEach { runner.forget(it); signatures.remove(it) }
        mirrors.forEach { entry ->
            val signature = "${entry.mode}|${canonicalAddress(entry)}"
            if (signatures[entry.id]?.let { it != signature } == true) runner.forget(entry.id)
            signatures[entry.id] = signature
        }
    }
    val ordered = sortMirrors(mirrors, runner.results, settings.mirrorOrder)
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
            if (session.state.readFailed) item(key = "mirror-read-error") {
                MirrorEditorFeedback("设置结果待确认，已暂停写入")
                TextButton(text = "重试读取", onClick = { session.retryRead() })
            }
            if (session.state.error != null && !page.present) item(key = "mirror-write-error") {
                MirrorEditorFeedback("设置未保存，已恢复上次确认值")
                TextButton(text = "关闭提示", onClick = { session.dismissError() })
            }
            item(key = "mirror-title") {
                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(MirrorIcons.Github, null, Modifier.size(24.dp))
                    Text("GitHub 镜像", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.semantics { heading() })
                }
            }
            item(key = "mirror-enabled") {
                SettingsCard {
                    ToggleSetting("启用镜像", value = settings.mirrorEnabled,
                        onChange = { enabled -> onChange { it.copy(mirrorEnabled = enabled) } },
                        leadingIcon = MirrorIcons.Power,
                        enabled = mirrors.isNotEmpty() && !session.state.readFailed)
                }
            }
            if (unfinished.isNotEmpty()) item(key = "mirror-drafts") {
                SettingsCard {
                    unfinished.forEach { draft -> key(draft.id) {
                        TextButton(
                            text = "${draft.name.text.ifBlank { "新增镜像" }} · 草稿",
                            onClick = { page.show(MirrorPageState.Panel.Edit(draft)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } }
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
                SettingsCard {
                    MirrorSourceHeader(settings.mirrorOrder) { onChange { it.copy(mirrorOrder = nextDisplayOrder(it.mirrorOrder)) } }
                    shown.forEach { entry -> key(entry.id) {
                        SourceDivider()
                        MirrorRow(
                            name = entry.name, address = hostOf(entry.address), selected = entry.id == selectedId,
                            selectionEnabled = settings.mirrorEnabled && !session.state.readFailed,
                            result = runner.result(entry.id), phase = runner.phase(entry.id),
                            onSelect = { if (settings.selectedMirrorId != entry.id) onChange { it.copy(selectedMirrorId = entry.id) } },
                            onMenu = { returnFocusId = entry.id; page.show(MirrorPageState.Panel.Manage(entry.id)) },
                            menuFocusRequester = menuFocus.getOrPut(entry.id) { FocusRequester() },
                        )
                    } }
                }
            }
        }
        if (mirrors.isNotEmpty()) MirrorProbeBar(
            runner, mirrors, enabled = !session.state.readFailed,
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
            is MirrorPageState.Panel.Manage -> mirrors.firstOrNull { it.id == panel.id }?.name ?: "镜像"
            is MirrorPageState.Panel.Delete -> "删除${mirrors.firstOrNull { it.id == panel.id }?.name ?: "镜像"}？"
            null -> ""
        },
        icon = if (panel is MirrorPageState.Panel.Delete) MirrorIcons.Trash else MirrorIcons.Github,
        backdrop = backdrop, glassEnabled = glassEnabled, dark = dark, materialMode = settings.materialMode,
        onDismiss = close,
        onDismissFinished = { page.finishDismiss() },
        footer = {
            if (editor != null) MirrorEditorSave(editor, session.state.readFailed,
                onSave = { page.submit(editor) }, onRetryRead = { session.retryRead() })
        },
    ) {
        when (panel) {
            is MirrorPageState.Panel.Edit -> MirrorEditor(panel.draft, settings.mirrors, session.state.readFailed,
                backdrop, glassEnabled, dark, settings.materialMode)
            is MirrorPageState.Panel.Manage -> MirrorManageContent(panel.id, settings, mirrors, runner,
                onEdit = { entry -> page.show(MirrorPageState.Panel.Edit(page.draft(entry))) },
                onDelete = { page.show(MirrorPageState.Panel.Delete(panel.id)) },
                onMove = { delta -> onChange { it.copy(mirrors = movedMirror(it.mirrors, panel.id, delta)) } },
                onTest = { runner.measure(panel.id, mirrors.firstOrNull { it.id == panel.id }); page.dismiss() },
                canWrite = !session.state.readFailed)
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
