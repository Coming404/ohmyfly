"""Source-wiring guards only, not native accessibility/render/lifecycle execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/dev/ohmydrift'
GLASS = COMMON / 'ui/glass'


class GlassAccessibilityTests(unittest.TestCase):
    def test_system_reduction_reaches_the_single_effect_gate(self):
        app = (COMMON / 'App.kt').read_text()
        preset = (GLASS / 'GlassPreset.kt').read_text()
        self.assertIn('val systemReduceTransparency by rememberSystemReduceTransparency()', app)
        self.assertIn('glassEffectsEnabled(reduceTransparency, glassShaderSupported(), systemReduceTransparency)', app)
        self.assertIn('!reduceTransparency && !systemReduceTransparency && shaderSupported', preset)
        self.assertEqual(2, app.count('glassEnabled = glassEnabled,'))
        self.assertIn('mirrorPage && mirrorOverlay && glassEnabled', app)
        # System accessibility input never replaces the saved application choice.
        self.assertNotIn('copy(reduceTransparency', app)
        for file in ('settings/AppSettings.kt', 'settings/SettingsCodec.kt'):
            self.assertNotIn('systemReduceTransparency', (COMMON / file).read_text())

    def test_ios_observes_changes_refreshes_foreground_and_disposes(self):
        ios = (ROOT / 'shared/src/iosMain/kotlin/dev/ohmydrift/ui/SystemTransparency.ios.kt').read_text()
        android = (ROOT / 'shared/src/androidMain/kotlin/dev/ohmydrift/ui/SystemTransparency.android.kt').read_text()
        contract = (COMMON / 'ui/SystemTransparency.kt').read_text()
        self.assertIn('expect fun rememberSystemReduceTransparency(): State<Boolean>', contract)
        self.assertIn('mutableStateOf(UIAccessibilityIsReduceTransparencyEnabled())', ios)
        for notification in ('UIAccessibilityReduceTransparencyStatusDidChangeNotification',
                             'UIApplicationDidBecomeActiveNotification'):
            self.assertIn(notification, ios)
        self.assertIn('NSOperationQueue.mainQueue) { refresh() }', ios)
        self.assertIn('onDispose { observers.forEach(center::removeObserver) }', ios)
        self.assertIn('remember { mutableStateOf(false) }', android)

    def test_fallback_stops_recording_and_requests_static_highlights(self):
        app = (COMMON / 'App.kt').read_text()
        surface = (GLASS / 'GlassNavigationSurface.kt').read_text()
        chrome = (COMMON / 'ui/ChromeMaterial.kt').read_text()
        highlight = (GLASS / 'GlassHighlight.kt').read_text()
        self.assertIn('if (glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier', app)
        self.assertIn('if (enabled) {', surface)
        self.assertLess(surface.index('if (enabled) {'), surface.index('.layerBackdrop(tabsBackdrop)'))
        self.assertIn('else Modifier.background(surface, NavigationBarShape)', surface)
        self.assertIn('base.background(surface)', chrome)
        self.assertIn('rememberGlassGravity(enabled && motionAllowed && preset.highlightAlpha > 0f)', highlight)

    def test_keyboard_focus_is_visible_above_the_lens_without_duplicate_targets(self):
        tabs = (GLASS / 'GlassNavigationTabs.kt').read_text()
        surface = (GLASS / 'GlassNavigationSurface.kt').read_text()
        self.assertIn('if (recording) Modifier else Modifier', tabs)
        self.assertIn('.onFocusChanged { onFocusChange(index, it.isFocused) }', tabs)
        self.assertIn('.selectable(', tabs)
        self.assertNotIn('.focusable(', tabs)
        self.assertIn('if (focused) focusedIndex = index else if (focusedIndex == index) focusedIndex = -1', surface)
        marker = surface.index('if (focusedIndex in AppDestination.entries.indices)')
        self.assertLess(surface.index('if (enabled) capsule.glassNavigationCapsule'), marker)
        focus = surface[marker:surface.index('/** The combined source')]
        self.assertIn('clearAndSetSemantics {}', focus)
        self.assertIn('glassTabOffset(focusedIndex.toFloat(), response.tabWidth, response.ltr)', focus)
        self.assertIn('.border(2.dp, MiuixTheme.colorScheme.onSurface, focusShape)', focus)
        self.assertNotIn('onDestination', focus)

    def test_settings_errors_are_associated_with_input_and_actions_can_wrap(self):
        controls = (COMMON / 'ui/settings/SettingsControls.kt').read_text()
        self.assertIn('TextField(value = draft, onValueChange = { draft = it }, label = title, modifier = Modifier.fillMaxWidth().semantics', controls)
        self.assertIn('SettingsInputMenu {', controls)
        self.assertIn('validationError?.let { error(it) }', controls)
        self.assertIn('liveRegion = LiveRegionMode.Polite', controls)
        self.assertEqual(2, controls.count('FlowRow('))
        self.assertIn('enabled = validationError == null && input != value', controls)
        self.assertNotIn('fontScale =', controls)


if __name__ == '__main__':
    unittest.main()
