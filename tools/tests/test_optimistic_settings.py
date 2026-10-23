"""Optimistic-setting source wiring guards, not coroutine/Kotlin execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'


class OptimisticSettingsTests(unittest.TestCase):
    def test_composition_reads_one_session_snapshot_directly(self):
        entry = (COMMON / 'PersistedSettings.kt').read_text()
        self.assertIn('remember(path, repository, retry)', entry)
        self.assertIn('SettingsSession(repo.data, repo::update)', entry)
        self.assertIn('LaunchedEffect(session) { session?.run() }', entry)
        self.assertIn('val state = session?.state', entry)
        self.assertIn('checkNotNull(session).change(transform)', entry)
        self.assertIn('state.error', entry)
        self.assertIn('session).dismissError()', entry)
        self.assertNotIn('collectAsState', entry)
        self.assertNotIn('rememberCoroutineScope', entry)

    def test_validation_and_ui_publish_precede_async_persistence(self):
        session = (COMMON / 'settings/SettingsSession.kt').read_text()
        self.assertIn('var state by mutableStateOf(SettingsUiState())', session)
        change = session.split('fun change(', 1)[1].split('fun dismissError', 1)[0]
        self.assertLess(change.index('validateSettings(next)'), change.index('pending.addLast(transform)'))
        self.assertLess(change.index('state = state.copy(settings = next'), change.index('activeScope.launch'))
        self.assertIn('update(pending.first())', session)
        self.assertNotIn('update { next', session)

    def test_stale_echoes_failure_and_lifetime_have_explicit_paths(self):
        session = (COMMON / 'settings/SettingsSession.kt').read_text()
        self.assertIn('if (pending.isEmpty()) reader?.cancel()', session)
        self.assertIn('confirmed = values.first()', session)
        self.assertIn('pending.removeFirst()', session)
        self.assertIn('rollback(SAVE_ERROR)', session)
        self.assertIn('pending.clear()', session)
        self.assertIn('settings = confirmed, error = error', session)
        self.assertIn('catch (cancelled: CancellationException)', session)
        self.assertIn('currentCoroutineContext().ensureActive()', session)
        self.assertIn('scope = null', session)
        self.assertIn('if (state.readFailed) return', session)


if __name__ == '__main__':
    unittest.main()
