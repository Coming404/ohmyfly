"""Optimistic-setting source wiring guards, not coroutine/Kotlin execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'


class OptimisticSettingsTests(unittest.TestCase):
    def test_composition_renders_defaults_and_retries_repository_creation(self):
        entry = (COMMON / 'PersistedSettings.kt').read_text()
        self.assertIn('remember(path, repository, retry)', entry)
        self.assertIn('SettingsSession(repo.data, repo::update)', entry)
        self.assertIn('LaunchedEffect(session) { session.run() }', entry)
        self.assertIn('val state = session.state', entry)
        self.assertIn('val current = state.settings', entry)
        self.assertNotIn('checkNotNull', entry)
        self.assertIn('val retryRead: () -> Unit = if (repository == null) ({ retry += 1 }) else session::retryRead', entry)
        self.assertNotIn('MirrorDialog(', entry)
        self.assertNotIn('MiuixTheme', entry)
        self.assertNotIn('collectAsState', entry)
        self.assertNotIn('rememberCoroutineScope', entry)
        self.assertNotIn('state.settings == null', entry)

    def test_first_read_must_finish_before_any_write(self):
        session = (COMMON / 'settings/SettingsSession.kt').read_text()
        self.assertIn('var state by mutableStateOf(SettingsUiState())', session)
        enqueue = session.split('private fun enqueue(', 1)[1].split('fun retryRead()', 1)[0]
        self.assertIn('activeScope == null || confirmed == null || state.readFailed', enqueue)
        self.assertLess(enqueue.index('validateSettings(next)'), enqueue.index('pending.addLast(change)'))
        self.assertLess(enqueue.index('state = state.copy(settings = next'), enqueue.index('activeScope.launch'))
        self.assertIn('update(pending.first().transform)', session)
        self.assertNotIn('update { next', session)

    def test_failure_dialog_lives_inside_scaffold_content(self):
        app = (COMMON / 'App.kt').read_text()
        entry = (COMMON / 'PersistedSettings.kt').read_text()
        self.assertNotIn('MirrorDialog(', entry)
        self.assertIn('MirrorDialog(', app)
        self.assertLess(app.index('Scaffold('), app.index('MirrorDialog('))
        dialog = app.split('MirrorDialog(', 1)[1]
        self.assertIn('show = settingsSession.state.readFailed', dialog)
        self.assertIn('dismissible = false', dialog)
        self.assertIn('TextButton(text = "重试", onClick = hapticClick(onRetryRead))', dialog)
        self.assertIn('"设置结果待确认"', dialog)
        self.assertIn('"读取设置失败"', dialog)

    def test_non_dismissible_dialog_consumes_back_inside_overlay_content(self):
        dialog = (COMMON / 'ui/settings/MirrorDialog.kt').read_text()
        self.assertIn('import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler', dialog)
        self.assertIn('import kotlinx.coroutines.flow.collect', dialog)
        self.assertIn('dismissible: Boolean = true', dialog)
        overlay_content = dialog.split('cornerRadius = DialogCornerRadius,\n    ) {', 1)[1]
        handler = overlay_content.split('PredictiveBackHandler(', 1)[1].split('Column(', 1)[0]
        self.assertIn('enabled = show && !dismissible', handler)
        self.assertIn('onProgress = { events -> events.collect {} }', handler)
        self.assertIn('onCommit = {}', handler)
        self.assertIn('onCancel = {}', handler)
        self.assertIn('show = show', dialog)
        self.assertIn('onDismissRequest = { if (dismissible) onDismiss() }', dialog)
        self.assertIn('onDismissFinished = onDismissFinished', dialog)
        self.assertIn('if (dismissible) {', overlay_content)
        self.assertNotIn('WindowDialog', dialog)
        self.assertNotIn('import top.yukonga.miuix.kmp.layout.', dialog)

    def test_stale_echoes_failure_and_lifetime_have_explicit_paths(self):
        session = (COMMON / 'settings/SettingsSession.kt').read_text()
        self.assertIn('if (pending.isEmpty()) reader?.cancel()', session)
        self.assertIn('confirmed = values.first()', session)
        self.assertIn('pending.removeFirst()', session)
        self.assertIn('rollback(reportFailure = true)', session)
        self.assertIn('pending.clear()', session)
        self.assertIn('settings = confirmed ?: state.settings, confirmedSettings = confirmed', session)
        self.assertNotIn('state.error', session)
        self.assertIn('catch (cancelled: CancellationException)', session)
        self.assertIn('currentCoroutineContext().ensureActive()', session)
        self.assertIn('scope = null', session)
        self.assertIn('confirmed == null || state.readFailed', session)


if __name__ == '__main__':
    unittest.main()
