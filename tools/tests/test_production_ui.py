"""Guard the production UI boundary; not a Kotlin/Swift compiler."""
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ProductionUiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        roots = [ROOT / path for path in (
            'shared/src/commonMain', 'shared/src/androidMain', 'shared/src/iosMain',
            'androidApp/src', 'iosApp',
        )]
        output = subprocess.check_output(
            ['rg', '--files', '-g', '*.kt', '-g', '*.swift',
             *[str(path) for path in roots if path.is_dir()]], text=True,
        )
        cls.sources = [Path(path) for path in output.splitlines()]

    def test_runtime_file_names_have_no_showcase_or_test_placeholders(self):
        def tokens(name):
            return {token.casefold() for token in re.findall(r'[A-Z]+(?=[A-Z][a-z]|$)|[A-Z]?[a-z]+|[0-9]+', name)}

        self.assertIn('test', tokens('SpeedTestScreen'))
        self.assertIn('demo', tokens('DemoNode'))
        self.assertFalse({'demo', 'test'} & tokens('LatestResults'))
        self.assertTrue(self.sources)
        for path in self.sources:
            with self.subTest(path=path.relative_to(ROOT)):
                self.assertFalse({'demo', 'test'} & tokens(path.stem))

    def test_runtime_sources_do_not_restore_fake_results_or_showcase_copy(self):
        markers = re.compile(
            r'\bDemo\w*|\bdemoNodes\b|\bsampleLatencyMs\b|'
            r'界面预览|非实测|演示节点|展开动效'
        )
        for path in self.sources:
            with self.subTest(path=path.relative_to(ROOT)):
                self.assertNotRegex(path.read_text(), markers)

    def test_dynamic_seed_is_connected_without_wallpaper_permissions(self):
        shared = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'
        host = (ROOT / 'androidApp/src/main/kotlin/com/ohmyfly/app/MainActivity.kt').read_text()
        app = (shared / 'App.kt').read_text()
        theme = (shared / 'ui/AppTheme.kt').read_text()
        ios = (ROOT / 'shared/src/iosMain/kotlin/com/ohmyfly/MainViewController.kt').read_text()
        manifest = (ROOT / 'androidApp/src/main/AndroidManifest.xml').read_text()
        self.assertIn('system_accent1_500', host)
        self.assertRegex(host, r'(?s)override fun onResume\(\).*?dynamicColorSeed = Color\(getColor\(android.R.color.system_accent1_500\)\)')
        self.assertIn('App(dynamicColorSeed = dynamicColorSeed', host)
        self.assertIn('AppTheme(dynamicColorSeed, dark)', app)
        self.assertIn('remember(dark, dynamicColorSeed)', theme)
        self.assertIn('appThemeController(dynamicColorSeed, dark)', theme)
        self.assertIn('keyColor = dynamicColorSeed', theme)
        self.assertIn('ThemePaletteStyle.Neutral', theme)
        self.assertIn('ThemeColorSpec.Spec2025', theme)
        self.assertIn('App(dynamicColorSeed = null', ios)
        self.assertNotRegex(manifest, r'READ_MEDIA|READ_EXTERNAL_STORAGE|READ_WALLPAPER')
    def test_settings_cards_share_one_surface_and_squircle_border(self):
        shared = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'
        app = (shared / 'App.kt').read_text()
        theme = (shared / 'ui/AppTheme.kt').read_text()
        controls = (shared / 'ui/settings/SettingsControls.kt').read_text()
        self.assertIn('LocalAppSurfaceColors.current.page', app)
        self.assertIn('drawRect(background)', app)
        self.assertIn('.background(background)', app)
        self.assertIn('CompositionLocalProvider(', theme)
        self.assertIn('LocalAppSurfaceColors provides surfaces', theme)
        self.assertIn('CardDefaults.defaultColors(color = surfaces.card, contentColor = surfaces.onCard)', controls)
        self.assertIn('.squircleBorder(', controls)
        self.assertIn('width = 0.5.dp', controls)
        self.assertIn('cornerRadius = CardDefaults.CornerRadius', controls)
        for path in self.sources:
            is_settings = path.parent == shared / 'ui/settings' or path.name == 'SettingsScreen.kt'
            if not is_settings or path.name == 'SettingsControls.kt':
                continue
            with self.subTest(path=path.relative_to(ROOT)):
                self.assertNotRegex(path.read_text(), r'(?<![\w])Card\(')

    def test_system_bars_share_the_applied_theme_without_keying_navigation(self):
        app = (ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly/App.kt').read_text()
        root = app.split('private fun AppContent(', 1)[0]
        self.assertEqual(1, app.count('isSystemInDarkTheme()'))
        self.assertIn('val dark = themeMode.isDark(isSystemInDarkTheme())', root)
        self.assertRegex(root, r'(?s)LaunchedEffect\(dark,\s*themeMode\)\s*\{\s*'
                               r'onThemeChanged\(dark,\s*themeMode == ThemeMode\.System\)')
        self.assertIn('AppContent(dynamicColorSeed, dark, settings', root)
        self.assertIn('AppTheme(dynamicColorSeed, dark)', app)
        self.assertNotRegex(app, r'\bkey\s*\([^)]*(?:dark|themeMode)')

    def test_ios_clears_manual_appearance_on_the_existing_swiftui_host(self):
        entry = (ROOT / 'shared/src/iosMain/kotlin/com/ohmyfly/MainViewController.kt').read_text()
        swift = (ROOT / 'iosApp/iosApp/ContentView.swift').read_text()
        self.assertIn('onThemeChanged = onThemeChanged', entry)
        self.assertRegex(swift, r'followsSystem\.boolValue\s*\?\s*nil\s*:\s*'
                                r'\(dark\.boolValue\s*\?\s*\.dark\s*:\s*\.light\)')
        self.assertEqual(1, swift.count('.preferredColorScheme(colorScheme)'))
        self.assertIn('.ignoresSafeArea()', swift)
        self.assertEqual(1, swift.count('MainViewControllerKt.MainViewController'))
        update = swift.split('func updateUIViewController', 1)[1]
        self.assertNotIn('MainViewControllerKt.MainViewController', update)
        self.assertNotRegex(swift, r'\.id\s*\(')
        self.assertNotIn('overrideUserInterfaceStyle', entry + swift)

    def test_android_updates_both_icon_sets_without_changing_system_settings(self):
        host = (ROOT / 'androidApp/src/main/kotlin/com/ohmyfly/app/MainActivity.kt').read_text()
        self.assertIn('enableEdgeToEdge()', host)
        self.assertRegex(host, r'(?s)App\([^)]*\)\s*\{\s*dark,\s*_\s*->')
        self.assertIn('WindowCompat.getInsetsController(window, window.decorView)', host)
        for bar in ('Status', 'Navigation'):
            self.assertIn(f'isAppearanceLight{bar}Bars = !dark', host)
        self.assertEqual(1, len(re.findall(r'\bsetContent\s*\{', host)))
        self.assertNotRegex(host, r'\b(recreate|setDefaultNightMode|setApplicationNightMode)\s*\(')


if __name__ == '__main__':
    unittest.main()
