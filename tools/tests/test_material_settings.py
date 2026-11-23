"""Material configuration wiring only; does not execute Kotlin or render shaders."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'


class MaterialSettingsTests(unittest.TestCase):
    def test_default_and_two_modes_are_shared_configuration(self):
        model = (MAIN / 'settings/AppSettings.kt').read_text()
        modes = (MAIN / 'settings/MaterialMode.kt').read_text()
        self.assertIn('val materialMode: MaterialMode = MaterialMode.LiquidGlass', model)
        self.assertEqual(2, modes.count('("'))
        self.assertIn('LiquidGlass("液态玻璃")', modes)
        self.assertIn('Expressive("Expressive")', modes)

    def test_home_uses_existing_choice_and_settings_callback(self):
        home = (MAIN / 'ui/settings/SettingsHome.kt').read_text()
        self.assertIn('ChoiceSetting("界面风格", settings.materialMode.name', home)
        self.assertIn('MaterialMode.entries.map { it.name to it.label }', home)
        self.assertRegex(home, r'onChange\("界面风格[^\n]+\) \{ it\.copy\(materialMode = MaterialMode\.valueOf\(name\)\) \}')
        self.assertNotIn('mutableStateOf', home)
        choice = (MAIN / 'ui/settings/ChoiceSetting.kt').read_text()
        self.assertIn('OverlayDropdownPopup(', choice)
        self.assertIn('DropdownArrowEndAction(', choice)
        self.assertIn('SettingsName(title)', choice)

    def test_codec_adds_optional_key_without_new_store_or_schema(self):
        codec = (MAIN / 'settings/SettingsCodec.kt').read_text()
        self.assertIn('const val CURRENT_SCHEMA_VERSION = 1', codec)
        self.assertIn('stringPreferencesKey("ui.materialMode")', codec)
        self.assertIn('target[materialMode] = settings.materialMode.name', codec)
        self.assertIn('preferences.readOrNull(materialMode).toEnumOr(defaults.materialMode)', codec)
        self.assertIn('booleanPreferencesKey("ui.glass.convexLensEnabled")', codec)
        self.assertIn('target[glassConvexLensEnabled] = settings.glassMaterial.convexLensEnabled', codec)
        self.assertIn('preferences.read(glassConvexLensEnabled, false)', codec)
        repository = (MAIN / 'settings/SettingsRepository.kt').read_text()
        self.assertIn('dataStore.edit { preferences ->', repository)
        self.assertIn('SettingsCodec.write(preferences, next)', repository)

    def test_glass_preview_surface_is_outside_its_recorded_backdrop(self):
        page = (MAIN / 'ui/settings/GlassMaterialSettingsPage.kt').read_text()
        preview = page.split('private fun GlassMaterialPreview(', 1)[1].split(
            '@Composable\nprivate fun PreviewNavigationItem(', 1,
        )[0]
        record_layer = preview.index('Box(Modifier.fillMaxSize().layerBackdrop(backdrop))')
        glass_materials = [index for index in range(len(preview)) if preview.startswith('.chromeMaterial(', index)]
        self.assertEqual(2, len(glass_materials))
        self.assertTrue(all(record_layer < index for index in glass_materials))
        self.assertIn('Brush.linearGradient(', preview)
        self.assertIn('CatalogToolbarAction("搜索"', preview)
        self.assertIn('CatalogToolbarAction("设置"', preview)
        self.assertIn('PreviewNavigationItem(', preview)
        self.assertIn('convexLensEnabled = currentSettings.convexLensEnabled', page)
        self.assertIn('title = "凸透镜折射"', page)

    def test_reduced_transparency_keeps_existing_fallback(self):
        app = (MAIN / 'App.kt').read_text()
        self.assertIn('settings.materialMode == MaterialMode.LiquidGlass &&', app)
        self.assertIn('glassEffectsEnabled(reduceTransparency, glassShaderSupported(), systemReduceTransparency)', app)
        topbar = (MAIN / 'AppTopBar.kt').read_text()
        self.assertEqual(2, (app + topbar).count('glassEnabled = glassEnabled,'))
        self.assertNotIn('materialMode =', (MAIN / 'PersistedSettings.kt').read_text())
        presets = (MAIN / 'ui/glass/GlassPreset.kt').read_text()
        self.assertIn('!reduceTransparency && !systemReduceTransparency && shaderSupported', presets)


if __name__ == '__main__':
    unittest.main()
