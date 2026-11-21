"""Motion wiring guards only; Kotlin/native rendering and gesture execution remain device checks."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'
UI = COMMON / 'ui'
GLASS = UI / 'glass'


class MotionPolicyTests(unittest.TestCase):
    def test_saved_app_restriction_uses_existing_optimistic_settings(self):
        model = (COMMON / 'settings/AppSettings.kt').read_text()
        codec = (COMMON / 'settings/SettingsCodec.kt').read_text()
        home = (UI / 'settings/SettingsHome.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        self.assertIn('val reduceMotion: Boolean = false', model)
        self.assertIn('booleanPreferencesKey("ui.reduceMotion")', codec)
        self.assertIn('CURRENT_SCHEMA_VERSION = 1', codec)
        self.assertIn('target[reduceMotion] = settings.reduceMotion', codec)
        self.assertIn('reduceMotion = preferences.read(reduceMotion, defaults.reduceMotion)', codec)
        self.assertIn('ToggleSetting("减少动态效果", value = settings.reduceMotion', home)
        self.assertRegex(home, r'onChange\("减少动态效果[^\n]+\) \{ it\.copy\(reduceMotion = enabled\) \}')
        self.assertIn('AppMotion(settings.reduceMotion) {', app)
        self.assertNotIn('mutableStateOf(settings.reduceMotion)', home + app)
        for file in ('MotionSettingsTest.kt', 'MotionSettingsSessionTest.kt'):
            self.assertTrue((ROOT / 'shared/src/commonTest/kotlin/com/ohmyfly/settings' / file).exists())

    def test_system_scale_is_observed_not_replaced_or_written(self):
        policy = (UI / 'AppMotion.kt').read_text()
        self.assertIn('scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f', policy)
        self.assertIn('!reduceMotion && systemScale > 0f', policy)
        self.assertIn('LocalAppMotionAllowed provides appMotionAllowed(reduceMotion, systemScale)', policy)
        self.assertNotIn('MotionDurationScale {', policy)
        self.assertNotIn('scaleFactor =', policy)
        self.assertNotIn('rememberGlassMotionAllowed', (GLASS / 'GlassAppearance.kt').read_text())
        for file in (GLASS / 'GlassAppearance.kt', UI / 'SettingsScreen.kt'):
            self.assertIn('LocalAppMotionAllowed.current', file.read_text())

    def test_owned_transitions_return_targets_immediately_when_restricted(self):
        for file in (UI / 'AppThemeAnimation.kt', UI / 'AppSurfaceColors.kt'):
            source = file.read_text()
            self.assertIn('LaunchedEffect(target, motionAllowed)', source)
            self.assertIn('return if (motionAllowed) current else target', source)
            self.assertIn('tween(AppTransitionDurationMillis)', source)
        screen = (UI / 'SettingsScreen.kt').read_text()
        self.assertIn('if (motionAllowed) AppNavTransition else NavTransitions.None', screen)
        self.assertIn('else NavDisplayEffects.None', screen)
        self.assertIn('editorBackActive || !motionAllowed -> NavSwipeDirection.None', screen)
        self.assertNotIn('key(motionAllowed)', screen)
        self.assertIn('AppTransitionDurationMillis = 180', (UI / 'AppMotion.kt').read_text())

    def test_all_pinned_miuix_color_roles_are_interpolated_without_losing_mode(self):
        source = (UI / 'AppThemeAnimation.kt').read_text()
        fields = re.findall(r'^        (\w+) = mix\(start\.(\w+), end\.(\w+)\)', source, re.M)
        self.assertEqual(53, len(fields))
        self.assertEqual(53, len({field[0] for field in fields}))
        self.assertTrue(all(a == b == c for a, b, c in fields))
        for role in ('error', 'onError', 'disabledPrimarySlider', 'surfaceContainerHighest', 'sliderBackground'):
            self.assertIn(role, {field[0] for field in fields})
        theme = (UI / 'AppTheme.kt').read_text()
        self.assertEqual(1, len(re.findall(r'(?<!\w)ThemeController\(', theme)))
        self.assertIn('MiuixTheme(controller = controller)', theme)
        self.assertIn('val target = remember(controller) { rootColors.copy() }', theme)
        self.assertIn('MiuixTheme(colors = colors)', theme)
        self.assertIn('LocalContentColor provides colors.onBackground', theme)
        self.assertNotIn('LocalColorSchemeMode', theme)



if __name__ == '__main__':
    unittest.main()
