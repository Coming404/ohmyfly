package dev.ohmydrift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.MirrorOrder
import dev.ohmydrift.mirror.MirrorProbe
import dev.ohmydrift.mirror.createMirrorTransport
import dev.ohmydrift.mirror.sortMirrors
import dev.ohmydrift.settings.AppSettings
import dev.ohmydrift.ui.ChoiceOption
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import kotlinx.coroutines.flow.collect
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Selection is stored by id, so re-sorting never changes the chosen mirror.
 * While a row menu, the editor or the delete confirmation is open the shown order is frozen;
 * stable item keys keep LazyColumn anchored to the same row when it resumes.
 *
 * shortcut: screen-reader focus cannot be observed reliably from common code, so only the
 * stable keys plus the frozen order protect the anchor; revisit if Compose exposes focus state.
 */
@Composable
internal fun MirrorSettingsPage(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    contentPadding: PaddingValues,
    onEditorBackChange: ((() -> Unit)?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val runner = remember(scope) { MirrorProbeRunner(MirrorProbe(createMirrorTransport()), scope) }
    val state = rememberLazyListState()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MirrorEntry?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<MirrorEntry?>(null) }

    // The form stays open with its draft until the stored settings actually contain the entry;
    // a rejected write leaves the text in place and the parent shows the error.
    LaunchedEffect(settings.mirrors, pendingSave) {
        val pending = pendingSave ?: return@LaunchedEffect
        if (settings.mirrors.any { storedMatches(it, pending) }) {
            pendingSave = null
            editorOpen = false
            editing = null
        }
    }

    // Changed addresses invalidate their old measurement rather than showing it as current.
    val signatures = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(settings.mirrors) {
        settings.mirrors.forEach { entry ->
            val signature = "${entry.mode}|${entry.address}"
            if (signatures[entry.id]?.let { it != signature } == true) runner.forget(entry.id)
            signatures[entry.id] = signature
        }
    }

    val reportEditorBack by rememberUpdatedState(onEditorBackChange)
    val closeEditor: () -> Unit = { pendingSave = null; editorOpen = false; editing = null }
    DisposableEffect(editorOpen) {
        reportEditorBack(if (editorOpen) closeEditor else null)
        onDispose { reportEditorBack(null) }
    }
    PredictiveBackHandler(
        enabled = editorOpen,
        onProgress = { events -> events.collect {} },
        onCommit = closeEditor,
        onCancel = {},
    )
    if (editorOpen) {
        MirrorEditor(
            existing = editing,
            siblings = settings.mirrors,
            onCancel = { pendingSave = null; editorOpen = false; editing = null },
            onSubmit = { entry ->
                val stored = settings.mirrors.firstOrNull { it.id == entry.id }
                if (stored != null && storedMatches(stored, entry)) {
                    editorOpen = false
                    editing = null
                } else {
                    pendingSave = entry
                    onChange { current ->
                        current.copy(
                            mirrors = if (current.mirrors.any { it.id == entry.id }) {
                                current.mirrors.map { if (it.id == entry.id) entry else it }
                            } else {
                                current.mirrors + entry
                            },
                        )
                    }
                }
            },
            contentPadding = contentPadding,
        )
        return
    }

    val interacting = menuFor != null || confirmDelete != null
    val ordered = sortMirrors(settings.mirrors, runner.results, settings.mirrorOrder)
    var frozen by remember { mutableStateOf<List<MirrorEntry>?>(null) }
    LaunchedEffect(interacting) { frozen = if (interacting) ordered else null }
    val shown = frozen ?: ordered
    val selectedId = settings.selectedMirrorId

    SettingsList(contentPadding, state) {
        item { CurrentMirrorCard(settings, selectedId) }
        item { ProbeActions(runner, settings.mirrors, onAdd = { editing = null; editorOpen = true }) }
        item { SmallTitle("排序") }
        item {
            SettingsCard {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DISPLAY_ORDERS.forEach { order ->
                        ChoiceOption(
                            label = order.label,
                            selected = settings.mirrorOrder == order,
                            onClick = { onChange { it.copy(mirrorOrder = order) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item { SmallTitle("线路") }
        item {
            SettingsCard {
                MirrorDirectRow(
                    selected = selectedId == null,
                    result = runner.result(DIRECT_MIRROR_KEY),
                    phase = runner.phase(DIRECT_MIRROR_KEY),
                    testing = runner.running,
                    onSelect = { onChange { it.copy(selectedMirrorId = null) } },
                    onTest = { runner.measure(DIRECT_MIRROR_KEY, null) },
                )
            }
        }
        items(shown, key = { it.id }) { entry ->
            SettingsCard {
                MirrorEntryRow(
                    entry = entry,
                    selected = entry.id == selectedId,
                    result = runner.result(entry.id),
                    phase = runner.phase(entry.id),
                    menuOpen = menuFor == entry.id,
                    manualOrder = settings.mirrorOrder == MirrorOrder.Manual,
                    canMoveUp = settings.mirrors.indexOfFirst { it.id == entry.id } > 0,
                    canMoveDown = settings.mirrors.indexOfFirst { it.id == entry.id } in 0 until settings.mirrors.lastIndex,
                    testing = runner.running,
                    actions = MirrorRowActions(
                        select = { onChange { it.copy(selectedMirrorId = entry.id) } },
                        test = { runner.measure(entry.id, entry) },
                        menu = { menuFor = if (menuFor == entry.id) null else entry.id },
                        edit = {
                            if (runner.phase(entry.id) != MirrorTestPhase.Idle) runner.stop()
                            editing = entry; editorOpen = true; menuFor = null
                        },
                        moveUp = { onChange { it.copy(mirrors = movedMirror(it.mirrors, entry.id, -1)) } },
                        moveDown = { onChange { it.copy(mirrors = movedMirror(it.mirrors, entry.id, 1)) } },
                        delete = {
                            if (runner.phase(entry.id) != MirrorTestPhase.Idle) runner.stop()
                            confirmDelete = entry.id; menuFor = null
                        },
                    ),
                )
            }
        }
        confirmDelete?.let { id ->
            item { DeleteMirrorCard(settings, id, runner, onChange, onClose = { confirmDelete = null }) }
        }
        if (runner.stopped) {
            item { Note("已停止；已完成的结果保留，其余仍为未测试。") }
        }
        item { Note(HONESTY_NOTE) }
    }
}

/**
 * The persisted copy may normalize name and address, so compare identity, target and name.
 * A rejected name-only edit therefore keeps the draft instead of closing silently.
 */
private fun storedMatches(stored: MirrorEntry, pending: MirrorEntry): Boolean {
    val sameName = stored.name.trim() == pending.name.trim() || stored.name == hostOf(pending.address)
    return stored.id == pending.id &&
        sameName &&
        stored.mode == pending.mode &&
        canonicalAddress(stored) == canonicalAddress(pending)
}

private val DISPLAY_ORDERS = listOf(
    MirrorOrder.LatencyAscending,
    MirrorOrder.LatencyDescending,
    MirrorOrder.Manual,
)

private const val HONESTY_NOTE =
    "候选未经测试，探测通过不代表运营方可信。探测只请求固定公开样本的 HTTP 首字节并校验内容，不是 Ping，不携带任何凭据。"

@Composable
private fun DeleteMirrorCard(
    settings: AppSettings,
    id: String,
    runner: MirrorProbeRunner,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onClose: () -> Unit,
) {
    val entry = settings.mirrors.firstOrNull { it.id == id }
    SettingsCard {
        BasicComponent(
            title = "删除「${entry?.name ?: "该镜像"}」？",
            summary = when {
                entry == null -> "该镜像已不存在。"
                entry.id == settings.selectedMirrorId -> "这是当前使用的镜像；删除后改用直连。"
                else -> "仅从本地列表删除，重新使用需再次添加。"
            },
        )
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = if (entry?.id == settings.selectedMirrorId) "删除并改用直连" else "删除",
                onClick = {
                    if (runner.phase(id) != MirrorTestPhase.Idle) runner.stop()
                    onChange { current ->
                        current.copy(
                            mirrors = removedMirror(current.mirrors, id),
                            selectedMirrorId = current.selectedMirrorId.takeIf { it != id },
                        )
                    }
                    runner.forget(id)
                    onClose()
                },
                colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
            )
            TextButton(text = "取消", onClick = onClose)
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

private val MirrorOrder.label: String
    get() = when (this) {
        MirrorOrder.LatencyAscending -> "延迟低到高"
        MirrorOrder.LatencyDescending -> "延迟高到低"
        MirrorOrder.Manual -> "手动"
    }
