"""NavDisplay integration guards, not Kotlin compilation or device gesture tests."""
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'


class SettingsNavigationTests(unittest.TestCase):
    def test_saved_stack_replaces_independent_current_page(self):
        app = (COMMON / 'App.kt').read_text()
        routes = (COMMON / 'ui/settings/SettingsPage.kt').read_text()
        self.assertIn('rememberNavBackStack<SettingsPage>(SettingsPage.Home)', app)
        self.assertIn('val settingsPage = settingsStack.last() as SettingsPage', app)
        self.assertNotIn('mutableStateOf(SettingsPage.', app)
        self.assertIn('backStack = settingsStack', app)
        self.assertIn('onDestination = { destination = it }', app)
        self.assertIn('Saver<AppDestination, String>', app)
        self.assertIn('save = { it.name }', app)
        self.assertIn('@Serializable', routes)
        self.assertIn('SettingsPage(val title: String) : NavKey', routes)
        self.assertIn('lastOrNull() == SettingsPage.Home && page != SettingsPage.Home', routes)
        self.assertIn('if (size > 1) removeAt(lastIndex)', routes)

    def test_navdisplay_really_hosts_all_routes_in_existing_scaffold(self):
        screen = (COMMON / 'ui/SettingsScreen.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        self.assertIn('NavDisplay(', screen)
        self.assertIn('backStack = backStack', screen)
        self.assertIn('onBack = onBack', screen)
        self.assertIn('entry<SettingsPage>(swipeDismiss = swipeDirection)', screen)
        for page in ('Home', 'Speed', 'Media', 'Subscription', 'Mirrors', 'Privacy', 'About'):
            self.assertIn(f'SettingsPage.{page} ->', screen)
        self.assertNotIn('key(page)', screen)
        self.assertNotIn('Scaffold(', screen)
        self.assertIn('.background(background)', screen)
        self.assertIn('summary = writeError', screen)
        self.assertIn('SettingsButton("关闭提示", DriftIcons.Clear, onClick = onDismissError)', screen)
        self.assertEqual(1, app.count('Scaffold('))
        self.assertIn('Modifier.layerBackdrop(backdrop)', app)

    def test_custom_spring_transition_and_directional_swipe(self):
        screen = (COMMON / 'ui/SettingsScreen.kt').read_text()
        nav_transition = (COMMON / 'ui/AppNavTransition.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        self.assertIn('transition = if (motionAllowed) AppNavTransition else NavTransitions.None', screen)
        self.assertIn('navGraphicsTransition(', nav_transition)
        self.assertIn('motion = appNavMotion', nav_transition)
        self.assertIn('NavSettleSpec.Spring(', nav_transition)
        self.assertIn('dampingRatio = 0.72f', nav_transition)
        self.assertIn('stiffness = 400f', nav_transition)
        self.assertNotIn('PagerNavigationSpringSpec', nav_transition)
        self.assertIn('translationX = appNavTranslationX(', nav_transition)
        self.assertNotIn('coerceIn', nav_transition)
        self.assertIn('alpha = 1f', nav_transition)
        self.assertNotIn('roundToInt', nav_transition)
        self.assertIn('clampOvershoot = false', nav_transition)
        for phase in ('commit', 'cancel', 'programmatic'):
            self.assertIn(f'{phase} = appNavSettleSpec', nav_transition)
        self.assertIn('enableCornerClip = false', screen)
        self.assertIn('dimAmount = 0f', screen)
        self.assertIn('backdropColor = background', screen)
        self.assertIn('Modifier.fillMaxSize().clipToBounds()', screen)
        self.assertIn('blockInputDuringTransition = true', screen)
        self.assertIn('editorBackActive || !motionAllowed -> NavSwipeDirection.None', screen)
        self.assertIn('direction == LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft', screen)
        self.assertIn('else -> NavSwipeDirection.LeftToRight', screen)
        self.assertNotIn('Crossfade', screen)
        self.assertNotIn('pointerInput', screen)
        self.assertIn('HorizontalPager(', app)
        self.assertIn('userScrollEnabled = false', app)
        self.assertIn('springAnimateToPage(targetPage)', app)
        self.assertIn('else pagerState.scrollToPage(targetPage)', app)

    def test_mirror_editor_back_precedes_route_and_preserves_cancelled_gestures(self):
        mirror = (COMMON / 'ui/settings/MirrorSettingsPage.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        self.assertIn('mirrorEditorBack?.invoke() ?: settingsStack.popSettingsPage()', app)
        self.assertIn('editorBackActive = mirrorEditorBack != null', app)
        self.assertIn('onEditorBackChange = { mirrorEditorBack = it }', app)
        self.assertIn('rememberUpdatedState(onEditorBackChange)', mirror)
        self.assertIn('reportBack(if (page.present) close else null)', mirror)
        self.assertIn('reportBack(null)', mirror)
        self.assertNotIn('reportOverlay', mirror)
        self.assertIn('MirrorDialog(', mirror)
        dialog = (COMMON / 'ui/settings/MirrorDialog.kt').read_text()
        self.assertIn('OverlayDialog(', dialog)
        self.assertIn('enableWindowDim = true', dialog)
        self.assertIn('Modifier.shadow(18.dp', dialog)
        self.assertNotIn('PredictiveBackHandler(', mirror)
        self.assertNotIn('storedMatches', mirror)
        owner = (COMMON / 'ui/settings/MirrorPageState.kt').read_text()
        dismiss = owner.split('fun dismiss()', 1)[1].split('fun finishDismiss()', 1)[0]
        self.assertNotIn('submit(', dismiss)
        self.assertIn('session.changeConfirmed', owner)
        for source in ('commonMain', 'androidMain', 'iosMain'):
            path = ROOT / f'shared/src/{source}/kotlin/com/ohmyfly/ui/settings/SettingsBackHandler.kt'
            self.assertFalse(path.exists())

    def test_serialization_plugin_matches_kotlin_and_tests_match_ci_gate(self):
        catalog = (ROOT / 'gradle/libs.versions.toml').read_text()
        build = (ROOT / 'shared/build.gradle.kts').read_text()
        root_build = (ROOT / 'build.gradle.kts').read_text()
        self.assertIn('id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin"', catalog)
        self.assertIn('serialization = "1.11.0"', catalog)
        self.assertIn('implementation(libs.kotlinx.serialization.json)', build)
        self.assertIn('alias(libs.plugins.kotlinSerialization)', build)
        self.assertIn('alias(libs.plugins.kotlinSerialization) apply false', root_build)
        self.assertNotIn('implementation(libs.androidx.activity.compose)', build)
        test_file = ROOT / 'shared/src/commonTest/kotlin/com/ohmyfly/ui/settings/SettingsNavigationTest.kt'
        self.assertEqual(6, test_file.read_text().count('@Test'))
        files = subprocess.check_output(
            ['rg', '--files', str(ROOT / 'shared/src/commonTest'), '-g', '*.kt'], text=True,
        ).splitlines()
        count = sum(Path(p).read_text().count('@Test') for p in files)
        gate = (ROOT / '.github/workflows/build.yml').read_text()
        nav_test = ROOT / 'shared/src/commonTest/kotlin/com/ohmyfly/ui/AppNavTransitionTest.kt'
        self.assertEqual(8, nav_test.read_text().count('@Test'))
        self.assertEqual(218, count)
        floor = int(re.search(r'if total < (\d+) or failed', gate).group(1))
        self.assertEqual(190, floor)
        self.assertGreaterEqual(count, floor)


if __name__ == '__main__':
    unittest.main()
