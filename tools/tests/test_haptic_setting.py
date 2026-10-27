import re
import unittest
from pathlib import Path


COMMON = Path(__file__).resolve().parents[2] / 'shared/src/commonMain/kotlin/com/ohmyfly'


class HapticSettingTests(unittest.TestCase):
    def test_setting_expands_inside_the_existing_appearance_card(self):
        home = (COMMON / 'ui/settings/SettingsHome.kt').read_text()
        appearance = home.split('item(key = "appearance")', 1)[1].split('item(key = "tests-title")', 1)[0]
        self.assertIn('HapticSetting(settings, onChange)', appearance)
        setting = (COMMON / 'ui/settings/HapticSetting.kt').read_text()
        self.assertIn('rememberSaveable', setting)
        self.assertIn('onClick = hapticClick { expanded = !expanded }', setting)
        self.assertIn('if (expanded)', setting)
        self.assertIn('rotationZ = if (expanded) 90f else if (rtl) 180f else 0f', setting)
        self.assertIn('stateDescription = if (expanded)', setting)

    def test_slider_previews_only_new_detents_and_disables_builtin_haptics(self):
        source = (COMMON / 'ui/settings/HapticSetting.kt').read_text()
        slider = source.split('Slider(', 1)[1]
        self.assertRegex(slider, r'valueRange\s*=\s*0f\.\.2f')
        self.assertRegex(slider, r'steps\s*=\s*1\b')
        self.assertIn('enabled = settings.hapticEnabled', slider)
        self.assertIn('hapticEffect = SliderDefaults.SliderHapticEffect.None', slider)
        self.assertRegex(slider, re.compile(
            r'if \(next != level\)\s*\{\s*level = next\s*'
            r'onChange \{ it.copy\(hapticLevel = next\) \}\s*feedback.preview\(next\)',
        ))
        self.assertNotIn('feedback.click()', slider)

    def test_root_uses_persisted_settings_without_replacing_press_indications(self):
        app = (COMMON / 'App.kt').read_text()
        self.assertIn('AppHaptics(settings.hapticEnabled, settings.hapticLevel)', app)
        provider = (COMMON / 'ui/AppHaptics.kt').read_text()
        self.assertNotIn('PressInteraction', provider)
        self.assertNotIn('LocalIndication', provider)


if __name__ == '__main__':
    unittest.main()
