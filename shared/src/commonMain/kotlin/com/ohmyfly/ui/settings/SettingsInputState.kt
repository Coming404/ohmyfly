package com.ohmyfly.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.input.TextFieldValue
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.settings.SettingsWriteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal val LocalSettingsEdits = staticCompositionLocalOf<SettingsEdits> {
    error("SettingsEdits must be provided by SettingsScreen")
}

internal enum class SettingFinish { Done, Blur, Collapse, Retry }

/** Screen-local drafts survive lazy-item disposal; the session remains the only settings store. */
internal class SettingsEdits(val session: SettingsSession, private val scope: CoroutineScope) {
    private val drafts = mutableMapOf<Pair<SettingsPage, String>, SettingDraftState>()

    fun draft(
        page: SettingsPage,
        key: String,
        read: (AppSettings) -> String,
        validate: (String) -> String?,
        write: (AppSettings, String) -> AppSettings,
    ): SettingDraftState = drafts.getOrPut(page to key) {
        SettingDraftState(read(session.state.confirmedSettings ?: session.state.settings), read, validate, write, name = key)
    }

    fun input(draft: SettingDraftState, value: TextFieldValue) {
        val deferred = draft.input(value)
        if (deferred != null) finish(draft, deferred)
    }

    fun focus(draft: SettingDraftState, focused: Boolean) {
        if (draft.focus(focused)) finish(draft, SettingFinish.Blur)
    }

    fun finish(draft: SettingDraftState, reason: SettingFinish) {
        if (session.state.readFailed) return
        val candidate = draft.begin(reason) ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                draft.complete(session.changeConfirmed(name = draft.name, reportErrors = false) { current -> draft.write(current, candidate) })
            } finally {
                draft.saving = false
                session.state.confirmedSettings?.let { draft.sync(draft.read(it)) }
            }
        }
    }

    fun sync(settings: AppSettings) {
        drafts.values.forEach { it.sync(it.read(settings)) }
    }

    suspend fun reset(target: SettingsResetTarget): SettingsWriteResult {
        val result = session.changeConfirmed(
            name = if (target.page == null) "全部默认设置" else "本页默认设置",
            successText = if (target.page == null) "已恢复全部默认设置" else "已恢复本页默认设置",
            transform = target::apply,
        )
        if (result is SettingsWriteResult.Confirmed) {
            drafts.filterKeys { target.page == null || it.first == target.page }.values.forEach {
                it.sync(it.read(result.settings), reset = true)
            }
        }
        return result
    }
}

internal class SettingDraftState(
    initial: String,
    val read: (AppSettings) -> String,
    private val validate: (String) -> String?,
    val write: (AppSettings, String) -> AppSettings,
    val name: String? = null,
) {
    var value by mutableStateOf(TextFieldValue(initial))
        private set
    var expanded by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
        private set
    private var confirmed = initial
    private var focused = false
    private var revision = 0
    private var focusEpoch = 0
    private var lastBoundary: Pair<Int, Int>? = null
    private var deferred: SettingFinish? = null
    private var external: Pair<String, Boolean>? = null
    private var closeAfterSave = false

    fun open() { expanded = true }

    fun input(next: TextFieldValue): SettingFinish? {
        if (next.text != value.text) {
            revision++
            error = null
        }
        value = next
        if (next.composition != null) return null
        external?.let { (text, reset) ->
            external = null
            sync(text, reset)
            deferred = null
            return null
        }
        return deferred.also { deferred = null }
    }

    fun focus(next: Boolean): Boolean {
        val lost = focused && !next
        if (next && !focused) {
            focusEpoch++
            deferred = null
        }
        focused = next
        return lost
    }

    fun begin(reason: SettingFinish): String? {
        if (saving) {
            if (reason == SettingFinish.Collapse) closeAfterSave = true
            return null
        }
        if (value.composition != null) {
            if (deferred != SettingFinish.Collapse) deferred = reason
            return null
        }
        val boundary = revision to focusEpoch
        if ((reason == SettingFinish.Done || reason == SettingFinish.Blur) && lastBoundary == boundary) return null
        lastBoundary = boundary
        error = validate(value.text)
        if (error != null) { expanded = true; return null }
        if (value.text == confirmed) {
            if (reason == SettingFinish.Collapse) expanded = false
            return null
        }
        closeAfterSave = reason == SettingFinish.Collapse
        saving = true
        return value.text
    }

    fun complete(result: SettingsWriteResult) {
        saving = false
        when (result) {
            is SettingsWriteResult.Confirmed -> {
                confirmed = read(result.settings)
                if (value.text != confirmed) value = value.copy(text = confirmed, composition = null)
                error = null
                if (closeAfterSave) expanded = false
            }
            SettingsWriteResult.Failed -> fail("保存失败，草稿已保留，请重试。")
            SettingsWriteResult.Uncertain -> fail("写入结果待确认，草稿已保留，请重试读取。")
            SettingsWriteResult.Rejected -> fail("暂时无法保存，草稿已保留，请检查输入或重试读取。")
        }
    }

    fun sync(text: String, reset: Boolean = false) {
        if (saving || (!reset && text == confirmed)) return
        if (value.composition != null) {
            external = text to (reset || external?.second == true)
            return
        }
        confirmed = text
        if (value.text != text) value = value.copy(text = text, composition = null)
        error = null
        deferred = null
        if (reset) expanded = false
    }

    private fun fail(message: String) {
        error = message
        expanded = true
    }
}
