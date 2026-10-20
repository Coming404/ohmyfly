"""Settings layout/wiring guards; these do not execute Compose interactions."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'shared/src/commonMain/kotlin/dev/ohmydrift/ui'
SETTINGS = UI / 'settings'


class SettingsHomeTests(unittest.TestCase):
    def test_identity_reuses_brand_and_runtime_version(self):
        home = (SETTINGS / 'SettingsHome.kt').read_text()
        screen = (UI / 'SettingsScreen.kt').read_text()
        self.assertIn('SettingsHome(settings, onChange, onPage, version', screen)
        self.assertIn('SettingsIdentity(version)', home)
        self.assertIn('Icon(DriftIcons.Wave', home)
        self.assertIn('Modifier.size(40.dp)', home)
        self.assertIn('Text(version, fontSize = 12.sp', home)
        self.assertIn('订阅管理与节点测速', home)
        self.assertNotIn('summary =', home)

    def test_home_preserves_six_subpages_with_persisted_material(self):
        home = (SETTINGS / 'SettingsHome.kt').read_text()
        for page in ('Speed', 'Media', 'Subscription', 'Mirrors', 'Privacy', 'About'):
            with self.subTest(page=page):
                self.assertIn(f'onPage(SettingsPage.{page})', home)
        self.assertIn('ChoiceSetting("界面材质", settings.materialMode.name', home)
        self.assertIn('it.copy(materialMode = MaterialMode.valueOf(name))', home)
        self.assertNotIn('  ›', home)
        self.assertIn('value = settings.mirrors.firstOrNull', home)

    def test_action_rows_are_navigation_only(self):
        row = (SETTINGS / 'SettingsActionRow.kt').read_text()
        self.assertIn('BasicComponent(', row)
        self.assertIn('role = Role.Button', row)
        self.assertIn('onClickLabel = "打开$title"', row)
        self.assertIn('MiuixIcons.Basic.ArrowRight', row)
        self.assertIn('scaleX = if (rtl) -1f else 1f', row)
        self.assertIn('Text(title, fontSize = 16.sp)', row)
        self.assertNotIn('expanded', row)
        self.assertNotIn('rotationZ', row)

    def test_presets_use_official_dropdown_without_inline_state(self):
        choice = (SETTINGS / 'ChoiceSetting.kt').read_text()
        option = (UI / 'ChoiceOption.kt').read_text()
        controls = (SETTINGS / 'SettingsControls.kt').read_text()
        app = (UI.parent / 'App.kt').read_text()
        self.assertIn('top.yukonga.miuix.kmp.preference.OverlayDropdownPreference', choice)
        self.assertIn('OverlayDropdownPreference(', choice)
        self.assertIn('items = options.map { it.second }', choice)
        self.assertIn('selectedIndex = options.indexOfFirst { it.first == value }', choice)
        self.assertIn('onSelectedIndexChange = { index -> onChange(options[index].first) }', choice)
        self.assertIn('Scaffold(', app)
        for name in ('SettingsHome.kt', 'SpeedSettingsPage.kt', 'ChoiceSetting.kt'):
            page = (SETTINGS / name).read_text()
            with self.subTest(page=name):
                self.assertNotIn('Expanded', page)
                self.assertNotIn('expanded', page)
                self.assertNotIn('mutableStateOf', page)
        self.assertNotIn('ChoiceOption', choice)
        self.assertNotIn('Column(', choice)
        self.assertNotIn('AnimatedVisibility', choice)
        self.assertIn('role = Role.RadioButton', option)
        self.assertIn('RadioButton(selected = selected, onClick = null', option)
        self.assertIn('Modifier.clearAndSetSemantics {}', option)
        self.assertNotIn('CircleShape', option)
        self.assertNotIn('DriftIcons.Check', option)
        self.assertNotIn('fun ChoiceSetting(', controls)
        self.assertIn('if (summary.isNotBlank())', controls)

    def test_spacing_is_shared_and_choices_are_not_persisted(self):
        controls = (SETTINGS / 'SettingsControls.kt').read_text()
        self.assertIn('horizontal = 16.dp', controls)
        self.assertIn('Arrangement.spacedBy(16.dp)', controls)
        choice = (SETTINGS / 'ChoiceSetting.kt').read_text()
        self.assertNotIn('rememberSaveable', choice)
        self.assertNotIn('AppSettings', choice)


if __name__ == '__main__':
    unittest.main()
