"""Settings layout/wiring guards; these do not execute Compose interactions."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly/ui'
SETTINGS = UI / 'settings'


class SettingsHomeTests(unittest.TestCase):
    def test_identity_reuses_brand_and_runtime_version(self):
        home = (SETTINGS / 'SettingsHome.kt').read_text()
        screen = (UI / 'SettingsScreen.kt').read_text()
        self.assertIn('SettingsHome(settings, onChange, onPage, version', screen)
        self.assertIn('SettingsIdentity(version)', home)
        self.assertIn('Icon(DriftIcons.Wave', home)
        self.assertIn('Modifier.size(32.dp)', home)
        self.assertIn('Text(version, fontSize = 14.sp', home)
        self.assertNotIn('塔台管理与航班起飞', home)
        identity = home.split('private fun SettingsIdentity(', 1)[1]
        self.assertNotIn('Column(', identity)
        self.assertIn('Text("OhMyFly", fontSize = 18.sp', identity)
        self.assertNotIn('summary =', home)

    def test_home_preserves_six_subpages_with_persisted_material(self):
        home = (SETTINGS / 'SettingsHome.kt').read_text()
        for page in ('Speed', 'Media', 'Subscription', 'Mirrors', 'Privacy', 'About'):
            with self.subTest(page=page):
                self.assertIn(f'onPage(SettingsPage.{page})', home)
        self.assertIn('ChoiceSetting("界面材质", settings.materialMode.name', home)
        self.assertIn('it.copy(materialMode = MaterialMode.valueOf(name))', home)
        self.assertNotIn('  ›', home)
        self.assertIn('value = if (!settings.mirrorEnabled) "已关闭"', home)
        self.assertIn('settings.mirrors.firstOrNull()?.name ?: "暂无镜像源"', home)

    def test_action_rows_are_navigation_only(self):
        row = (SETTINGS / 'SettingsActionRow.kt').read_text()
        self.assertIn('BasicComponent(', row)
        self.assertIn('role = Role.Button', row)
        self.assertIn('onClickLabel = "打开$title"', row)
        self.assertIn('MiuixIcons.Basic.ArrowRight', row)
        self.assertIn('scaleX = if (rtl) -1f else 1f', row)
        self.assertIn('SettingsName(title)', row)
        self.assertIn('startAction = { SettingsLeadingIcon(icon) }', row)
        self.assertNotIn('expanded', row)
        self.assertNotIn('rotationZ', row)

    def test_choices_retain_official_popup_and_accessible_custom_row(self):
        choice = (SETTINGS / 'ChoiceSetting.kt').read_text()
        option = (UI / 'ChoiceOption.kt').read_text()
        controls = (SETTINGS / 'SettingsControls.kt').read_text()
        app = (UI.parent / 'App.kt').read_text()
        self.assertIn('top.yukonga.miuix.kmp.popup.OverlayDropdownPopup', choice)
        self.assertIn('OverlayDropdownPopup(entry = entry, show = expanded', choice)
        self.assertIn('DropdownItem(text = label, selected = key == value', choice)
        self.assertIn('onClick = { if (key != value) onChange(key) }', choice)
        self.assertIn('DropdownArrowEndAction(', choice)
        self.assertIn('role = Role.DropdownList', choice)
        self.assertIn('onClickLabel = "选择$title"', choice)
        self.assertIn('onDismiss = { expanded = false }', choice)
        self.assertIn('collapseOnSelection = true', choice)
        self.assertIn('SettingsName(title)', choice)
        self.assertIn('SettingsLeadingIcon(icon)', choice)
        self.assertIn('Scaffold(', app)
        for name in ('SettingsHome.kt', 'SpeedSettingsPage.kt'):
            page = (SETTINGS / name).read_text()
            with self.subTest(page=name):
                self.assertNotIn('mutableStateOf', page)
                self.assertNotIn('OverlayDropdownPopup(', page)
        self.assertNotIn('ChoiceOption', choice)
        self.assertNotIn('AnimatedVisibility', choice)
        self.assertNotIn('PopupLayout(', choice)
        self.assertNotIn('chromeMaterial', choice)
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
