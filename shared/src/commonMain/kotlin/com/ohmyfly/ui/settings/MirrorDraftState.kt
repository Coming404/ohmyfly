package com.ohmyfly.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.mirror.normalizedMirrorAddress
import com.ohmyfly.mirror.validateMirror

/** The page owns text/selection/composition; a confirmed settings emission never replaces input. */
internal class MirrorDraftState(val id: String, existing: MirrorEntry?) {
    var name by mutableStateOf(TextFieldValue(existing?.name.orEmpty()))
    var address by mutableStateOf(TextFieldValue(existing?.address.orEmpty()))
    var confirmed by mutableStateOf(existing)
    var attempted by mutableStateOf(false)
    var failure by mutableStateOf<String?>(null)
    var uncertain by mutableStateOf(false)
    var saving by mutableStateOf(0)
    var lastSubmitted: MirrorEntry? = null
    var submission = 0L

    val composing: Boolean get() = name.composition != null || address.composition != null
    val candidate: MirrorEntry get() = MirrorEntry(id, name.text.trim(), address.text.trim())
    val unfinished: Boolean get() = saving > 0 || failure != null || uncertain ||
        (confirmed == null && (name.text.isNotBlank() || address.text.isNotBlank())) ||
        (confirmed != null && !sameMirrorCandidate(candidate, confirmed))
}

internal fun mirrorDraftError(entry: MirrorEntry, siblings: List<MirrorEntry>): String? =
    validateMirror(entry) ?: duplicateError(entry, siblings)

/** Name is not identity; address equality follows the persistence normalization contract. */
internal fun duplicateError(entry: MirrorEntry, siblings: List<MirrorEntry>): String? =
    siblings.firstOrNull {
        it.id != entry.id && canonicalAddress(it) == canonicalAddress(entry)
    }?.let { "与「${it.name}」的地址重复" }

internal fun canonicalAddress(entry: MirrorEntry): String = normalizedMirrorAddress(entry)

internal fun hostOf(address: String): String {
    val canonical = address.trim()
    val withoutScheme = canonical.substringAfter("://", canonical)
    return withoutScheme.substringBefore('/').substringBefore('?').ifBlank { "新镜像" }
}

internal fun sameMirrorCandidate(first: MirrorEntry, second: MirrorEntry?): Boolean = second != null &&
    first.id == second.id && first.name.trim() == second.name.trim() &&
    canonicalAddress(first) == canonicalAddress(second)
