"""Material configuration wiring only; does not execute Kotlin or render shaders."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'


class MaterialSettingsTests(unittest.TestCase):
    def test_default_and_four_options_are_shared_configuration(self):
        model = (MAIN / 'settings/AppSettings.kt').read_text()
        modes = (MAIN / 'settings/MaterialMode.kt').read_text()
        self.assertIn('val materialMode: MaterialMode = MaterialMode.LiquidGlass', model)
        self.assertEqual(4, modes.count('("'))
        for name, label in (('GaussianBlur', '高斯模糊'), ('HighlightGlass', '高光玻璃'),
                            ('SoftGlass', '柔光玻璃'), ('LiquidGlass', '液态玻璃')):
            with self.subTest(mode=name):
                self.assertIn(f'{name}("{label}")', modes)

    def test_home_uses_existing_choice_and_settings_callback(self):
        home = (MAIN / 'ui/settings/SettingsHome.kt').read_text()
        self.assertIn('ChoiceSetting("界面材质", settings.materialMode.name', home)
        self.assertIn('MaterialMode.entries.map { it.name to it.label }', home)
        self.assertIn('onChange { it.copy(materialMode = MaterialMode.valueOf(name)) }', home)
        self.assertNotIn('mutableStateOf', home)
        choice = (MAIN / 'ui/settings/ChoiceSetting.kt').read_text()
        self.assertIn('OverlayDropdownPreference(', choice)

    def test_codec_adds_optional_key_without_new_store_or_schema(self):
        codec = (MAIN / 'settings/SettingsCodec.kt').read_text()
        self.assertIn('const val CURRENT_SCHEMA_VERSION = 1', codec)
        self.assertIn('stringPreferencesKey("ui.materialMode")', codec)
        self.assertIn('target[materialMode] = settings.materialMode.name', codec)
        self.assertIn('preferences.readOrNull(materialMode).toEnumOr(defaults.materialMode)', codec)
        repository = (MAIN / 'settings/SettingsRepository.kt').read_text()
        self.assertIn('dataStore.edit { preferences ->', repository)
        self.assertIn('SettingsCodec.write(preferences, next)', repository)

    def test_reduced_transparency_keeps_existing_fallback(self):
        app = (MAIN / 'App.kt').read_text()
        self.assertIn('val glassEnabled = glassEffectsEnabled(reduceTransparency, glassShaderSupported(), systemReduceTransparency)', app)
        self.assertEqual(2, app.count('glassEnabled = glassEnabled,'))
        self.assertNotIn('materialMode =', (MAIN / 'PersistedSettings.kt').read_text())
        presets = (MAIN / 'ui/glass/GlassPreset.kt').read_text()
        self.assertIn('!reduceTransparency && !systemReduceTransparency && shaderSupported', presets)


if __name__ == '__main__':
    unittest.main()
