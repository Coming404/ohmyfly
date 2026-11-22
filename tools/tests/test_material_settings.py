"""Material configuration wiring only; does not execute Kotlin or render shaders."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'


class MaterialSettingsTests(unittest.TestCase):
    def test_default_and_two_options_are_shared_configuration(self):
        model = (MAIN / 'settings/AppSettings.kt').read_text()
        modes = (MAIN / 'settings/MaterialMode.kt').read_text()
        palettes = (MAIN / 'settings/AccentPalette.kt').read_text()
        self.assertIn('val materialMode: MaterialMode = MaterialMode.LiquidGlass', model)
        self.assertIn('val accentPalette: AccentPalette = AccentPalette.Automatic', model)
        self.assertIn('Automatic("自动", null)', palettes)
        self.assertEqual(8, palettes.count('(\"'))
        for name, label in (('LiquidGlass', '液态玻璃'), ('Expressive', 'Expressive')):
            with self.subTest(mode=name):
                self.assertIn(f'{name}("{label}")', modes)

    def test_home_uses_existing_choice_and_settings_callback(self):
        home = (MAIN / 'ui/settings/SettingsHome.kt').read_text()
        self.assertIn('ChoiceSetting("界面风格", settings.materialMode.name', home)
        self.assertIn('MaterialMode.entries.map { it.name to it.label }', home)
        self.assertRegex(home, r'onChange\("界面风格[^\n]+\) \{ it\.copy\(materialMode = MaterialMode\.valueOf\(name\)\) \}')
        self.assertIn('title = "配色"', home)
        self.assertIn('if (settings.materialMode == MaterialMode.Expressive)', home)
        self.assertIn('value = settings.accentPalette.name', home)
        self.assertIn('AccentPalette.entries.map { it.name to it.label }', home)
        self.assertIn('it.copy(accentPalette = palette)', home)
        theme = (ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly/ui/AppTheme.kt').read_text()
        self.assertIn('val seed = if (expressive)', theme)
        self.assertNotIn('mutableStateOf', home)
        choice = (MAIN / 'ui/settings/ChoiceSetting.kt').read_text()
        self.assertIn('OverlayCascadingListPopup(', choice)
        self.assertIn('DropdownArrowEndAction(', choice)
        self.assertIn('SettingsName(title)', choice)

    def test_codec_adds_optional_key_without_new_store_or_schema(self):
        codec = (MAIN / 'settings/SettingsCodec.kt').read_text()
        self.assertIn('const val CURRENT_SCHEMA_VERSION = 1', codec)
        self.assertIn('stringPreferencesKey("ui.materialMode")', codec)
        self.assertIn('stringPreferencesKey("ui.accentPalette")', codec)
        self.assertIn('target[materialMode] = settings.materialMode.name', codec)
        self.assertIn('target[accentPalette] = settings.accentPalette.name', codec)
        self.assertIn('preferences.readOrNull(materialMode).toEnumOr(defaults.materialMode)', codec)
        self.assertIn('preferences.readOrNull(accentPalette).toEnumOr(defaults.accentPalette)', codec)
        repository = (MAIN / 'settings/SettingsRepository.kt').read_text()
        self.assertIn('dataStore.edit { preferences ->', repository)
        self.assertIn('SettingsCodec.write(preferences, next)', repository)

    def test_glass_tuning_uses_actual_values_and_updates_every_surface(self):
        model = (MAIN / 'settings/GlassMaterialSettings.kt').read_text()
        page = (MAIN / 'ui/settings/GlassMaterialSettingsPage.kt').read_text()
        codec = (MAIN / 'settings/SettingsCodec.kt').read_text()
        self.assertIn('secondLayerAlpha: Float? = null', model)
        self.assertIn('thirdLayerAlpha: Float? = null', model)
        self.assertIn('blurRadiusDp: Float? = null', model)
        self.assertIn('navigationBarBaseMaterial(appearance.dark)', page)
        self.assertIn('referenceMaterial.second?.color?.alpha', page)
        self.assertIn('referenceMaterial.third?.color?.alpha', page)
        self.assertIn('referenceMaterial.blurRadius.value', page)
        self.assertGreaterEqual(page.count('steps = 0'), 1)
        self.assertIn('onValueChangeFinished =', page)
        self.assertIn('GlassMaterialPreview(previewSettings, referenceMaterial)', page)
        for file in ('ui/ChromeMaterial.kt', 'ui/NavigationBarMaterial.kt', 'ui/AppGlassSurfaces.kt'):
            self.assertIn('withSettings(', (MAIN / file).read_text())
        self.assertIn('settings.glassMaterial', (MAIN / 'App.kt').read_text())
        for key in ('secondLayerAlpha', 'thirdLayerAlpha', 'blurRadiusDp'):
            self.assertIn(f'ui.glass.{key}', codec)

    def test_reduced_transparency_keeps_existing_fallback(self):
        app = (MAIN / 'App.kt').read_text()
        self.assertIn('glassEffectsEnabled(reduceTransparency, glassShaderSupported(), systemReduceTransparency)', app)
        topbar = (MAIN / 'AppTopBar.kt').read_text()
        self.assertEqual(2, (app + topbar).count('glassEnabled = glassEnabled,'))
        self.assertNotIn('materialMode =', (MAIN / 'PersistedSettings.kt').read_text())
        presets = (MAIN / 'ui/glass/GlassAppearance.kt').read_text()
        self.assertIn('!reduceTransparency && !systemReduceTransparency && shaderSupported', presets)


if __name__ == '__main__':
    unittest.main()
