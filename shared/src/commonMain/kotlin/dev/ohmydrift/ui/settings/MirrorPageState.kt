package dev.ohmydrift.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.newMirrorId
import dev.ohmydrift.settings.SettingsSession
import dev.ohmydrift.settings.SettingsWriteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Single owner for modal replacement and session-local drafts, not a second settings store. */
internal class MirrorPageState(
    private val session: SettingsSession,
    private val scope: CoroutineScope,
) {
    sealed interface Panel {
        data class Manage(val id: String) : Panel
        data class Edit(val draft: MirrorDraftState) : Panel
        data class Delete(val id: String) : Panel
    }

    val drafts = mutableStateMapOf<String, MirrorDraftState>()
    private var newDraft: MirrorDraftState? = null
    var panel by mutableStateOf<Panel?>(null)
        private set
    var visible by mutableStateOf(false)
        private set
    var present by mutableStateOf(false)
        private set
    var deleting by mutableStateOf(false)
        private set
    var deleteError by mutableStateOf<String?>(null)
        private set

    fun draft(existing: MirrorEntry?): MirrorDraftState {
        if (existing != null) {
            val current = drafts[existing.id]
            if (current != null && (current.unfinished || sameMirrorCandidate(existing, current.confirmed))) return current
            return MirrorDraftState(existing.id, existing).also { drafts[it.id] = it }
        }
        newDraft?.takeIf { it.confirmed == null || it.unfinished }?.let { return it }
        val used = session.state.settings?.mirrors.orEmpty().map { it.id }.toSet() + drafts.keys
        return MirrorDraftState(newMirrorId(used), null).also {
            newDraft = it
            drafts[it.id] = it
        }
    }

    fun show(next: Panel) {
        panel = next
        visible = true
        present = true
        deleteError = null
    }

    fun dismiss() {
        val editing = (panel as? Panel.Edit)?.draft
        if (editing?.modeExpanded == true) {
            editing.modeExpanded = false
            return
        }
        if (deleting) return
        visible = false
    }

    fun finishDismiss() {
        if (!visible) {
            panel = null
            present = false
            drafts.values.filter { !it.unfinished && it.confirmed != null }.forEach { drafts.remove(it.id) }
        }
    }

    fun submit(draft: MirrorDraftState) {
        if (draft.composing || draft.saving > 0 || session.state.readFailed) return
        draft.attempted = true
        val candidate = draft.candidate
        if (mirrorDraftError(candidate, session.state.settings?.mirrors.orEmpty()) != null) return
        val entry = candidate.copy(address = canonicalAddress(candidate))
        if (sameMirrorCandidate(entry, draft.confirmed)) {
            draft.failure = null
            visible = false
            return
        }
        if (sameMirrorCandidate(entry, draft.lastSubmitted)) return
        draft.lastSubmitted = entry
        draft.failure = null
        draft.uncertain = false
        draft.saving++
        val request = ++draft.submission
        scope.launch {
            try {
                val outcome = session.changeConfirmed { current ->
                    current.copy(mirrors = if (current.mirrors.any { it.id == entry.id }) {
                        current.mirrors.map { if (it.id == entry.id) entry else it }
                    } else current.mirrors + entry)
                }
                when (outcome) {
                    is SettingsWriteResult.Confirmed -> {
                        draft.confirmed = outcome.settings.mirrors.firstOrNull { it.id == entry.id }
                        if (request == draft.submission) {
                            if (sameMirrorCandidate(entry, draft.confirmed)) {
                                draft.failure = null
                                if ((panel as? Panel.Edit)?.draft === draft && sameMirrorCandidate(draft.candidate, draft.confirmed)) visible = false
                            } else {
                                draft.lastSubmitted = null
                                draft.failure = "保存值已变化，请重试"
                            }
                        }
                    }
                    is SettingsWriteResult.Uncertain -> if (request == draft.submission) {
                        draft.uncertain = true
                        draft.failure = "结果待确认，草稿已保留"
                    }
                    else -> if (request == draft.submission) {
                        draft.lastSubmitted = null
                        draft.failure = "未能保存，草稿已保留"
                    }
                }
            } finally {
                draft.saving--
            }
        }
    }

    fun resolveRead() {
        val confirmed = session.state.confirmedSettings ?: return
        if (session.state.readFailed) return
        val deletingId = (panel as? Panel.Delete)?.id
        if (deletingId != null && !deleting && confirmed.mirrors.none { it.id == deletingId }) {
            deleteError = null
            visible = false
        }
        drafts.values.filter { it.uncertain }.forEach { draft ->
            val stored = confirmed.mirrors.firstOrNull { it.id == draft.id }
            draft.confirmed = stored
            draft.uncertain = false
            if (sameMirrorCandidate(draft.candidate, stored)) {
                draft.failure = null
            } else {
                draft.lastSubmitted = null
                draft.failure = "未能保存，草稿已保留"
            }
        }
    }

    fun delete(id: String) {
        if (deleting) return
        deleting = true
        deleteError = null
        scope.launch {
            try {
                when (session.changeConfirmed { current ->
                    val remaining = removedMirror(current.mirrors, id)
                    current.copy(
                        mirrors = remaining,
                        selectedMirrorId = current.selectedMirrorId?.takeIf { selected -> remaining.any { it.id == selected } }
                            ?: remaining.firstOrNull()?.id,
                        mirrorEnabled = current.mirrorEnabled && remaining.isNotEmpty(),
                    )
                }) {
                    is SettingsWriteResult.Confirmed -> {
                        drafts.remove(id)
                        if (newDraft?.id == id) newDraft = null
                        visible = false
                    }
                    is SettingsWriteResult.Uncertain -> deleteError = "删除结果待确认，请重试读取"
                    else -> deleteError = "未能删除，请重试"
                }
            } finally { deleting = false }
        }
    }
}
