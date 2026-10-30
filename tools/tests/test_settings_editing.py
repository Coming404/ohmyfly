"""Settings UI wiring guards. State/transaction behaviour is covered by commonTest,
not simulated here; these checks do not compile or render Compose.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly/ui'
SETTINGS = UI / 'settings'


class SettingsEditingIntegrationTests(unittest.TestCase):
    def test_editors_have_completion_boundaries_and_no_manual_save_cancel(self):
        controls = (SETTINGS / 'SettingsControls.kt').read_text()
        state = (SETTINGS / 'SettingsInputState.kt').read_text()
        screen = (UI / 'SettingsScreen.kt').read_text()
        self.assertIn('SettingsEditHost(session)', screen)
        self.assertIn('SettingsPageScope(page)', screen)
        for boundary in ('Done', 'Collapse'):
            self.assertIn(f'edits.finish(draft, SettingFinish.{boundary})', controls)
        self.assertIn('onFocusChanged { edits.focus(draft, it.isFocused) }', controls)
        self.assertIn('finish(draft, SettingFinish.Blur)', state)
        self.assertIn('session.changeConfirmed', state)
        self.assertNotRegex(controls, r'(?:TextButton|SettingsButton)\([^\n]*"(?:保存|取消)"')
        self.assertNotIn('remember(value, editing)', controls)
        for forbidden in ('delay(', 'debounce(', 'LaunchedEffect(value)'):
            self.assertNotIn(forbidden, controls + state)
        self.assertIn('draft.error?.let { error(it) }', controls)
        self.assertIn('SettingsHint(it, isError = true)', controls)
        self.assertIn('readOnly = draft.saving || edits.session.state.readFailed', controls)
        self.assertIn('if (session.state.readFailed) return', state)

    def test_typography_and_read_only_information_use_semantic_roles(self):
        components = (SETTINGS / 'SettingsComponents.kt').read_text()
        name = components.split('internal fun SettingsName(', 1)[1].split('@Composable', 1)[0]
        section = components.split('internal fun SettingsSectionTitle(', 1)[1].split('@Composable', 1)[0]
        info = components.split('internal fun SettingsInfo(', 1)[1].split('@Composable', 1)[0]
        self.assertIn('fontSize = 15.sp', name)
        self.assertIn('FontWeight.Medium', name)
        self.assertIn('fontSize = 15.sp', section)
        self.assertIn('FontWeight.Bold', section)
        self.assertIn('MiuixTheme.colorScheme.onSurface', section)
        self.assertIn('heading()', section)
        self.assertIn('fontSize = 12.sp', components)
        self.assertIn('LiveRegionMode.Polite', components)
        self.assertIn('contentDescription = null', components)
        for forbidden in ('onClick =', 'Role.Button', 'ArrowRight'):
            self.assertNotIn(forbidden, info)
        for page in ('SettingsHome', 'SpeedSettingsPage', 'MediaSettingsPage',
                     'SubscriptionSettingsPage', 'PrivacySettingsPage', 'AboutSettingsPage'):
            with self.subTest(page=page):
                text = (SETTINGS / f'{page}.kt').read_text()
                self.assertNotIn('SmallTitle(', text)
                self.assertNotIn('BasicComponent(', text)
        self.assertNotIn('Color.Black', components)

    def test_factory_reset_keeps_scope_and_confirmed_result_boundary(self):
        reset = (SETTINGS / 'ResetSettings.kt').read_text()
        state = (SETTINGS / 'SettingsInputState.kt').read_text()
        privacy = (SETTINGS / 'PrivacySettingsPage.kt').read_text()
        self.assertIn('ResetSettings(SettingsResetTarget.All)', privacy)
        self.assertIn('All -> AppSettings()', reset)
        self.assertNotIn('AppSettings(mirrors =', reset + privacy)
        self.assertIn('state.submit { edits.reset(target) }', reset)
        self.assertIn('transform = target::apply', state)
        self.assertIn('"已恢复全部默认设置" else "已恢复本页默认设置"', state)
        self.assertIn('name = draft.name, reportErrors = false', state)
        self.assertIn('target.page == null || it.first == target.page', state)
        self.assertIn('if (reset() is SettingsWriteResult.Confirmed) confirming = false', reset)
        self.assertNotIn('state.success', reset)
        self.assertNotIn('state.error', reset)
        self.assertIn('SettingsButton("取消"', reset)
        self.assertIn('destructive = true', reset)
        self.assertIn('自定义镜像将被移除', reset)
        components = (SETTINGS / 'SettingsComponents.kt').read_text()
        self.assertRegex(components, r'color = MiuixTheme.colorScheme.error,\s+contentColor = MiuixTheme.colorScheme.onError')
        self.assertIn('ButtonDefaults.buttonColorsPrimary()', components)


if __name__ == '__main__':
    unittest.main()
