"""Source-wiring guards only, not native accessibility/render/lifecycle execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'
GLASS = COMMON / 'ui/glass'


class GlassAccessibilityTests(unittest.TestCase):
    def test_system_reduction_reaches_the_single_effect_gate(self):
        app = (COMMON / 'App.kt').read_text()
        preset = (GLASS / 'GlassAppearance.kt').read_text()
        self.assertIn('val systemReduceTransparency by rememberSystemReduceTransparency()', app)
        self.assertIn('glassEffectsEnabled(reduceTransparency, glassShaderSupported(), systemReduceTransparency)', app)
        self.assertIn('!reduceTransparency && !systemReduceTransparency && shaderSupported', preset)
        topbar = (COMMON / 'AppTopBar.kt').read_text()
        self.assertEqual(2, (app + topbar).count('glassEnabled = glassEnabled,'))
        self.assertNotIn('mirrorOverlay', app)
        self.assertNotIn('.blur(depth)', app)
        # System accessibility input never replaces the saved application choice.
        self.assertNotIn('copy(reduceTransparency', app)
        for file in ('settings/AppSettings.kt', 'settings/SettingsCodec.kt'):
            self.assertNotIn('systemReduceTransparency', (COMMON / file).read_text())

    def test_ios_observes_changes_refreshes_foreground_and_disposes(self):
        ios = (ROOT / 'shared/src/iosMain/kotlin/com/ohmyfly/ui/SystemTransparency.ios.kt').read_text()
        android = (ROOT / 'shared/src/androidMain/kotlin/com/ohmyfly/ui/SystemTransparency.android.kt').read_text()
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
        chrome = (COMMON / 'ui/ChromeMaterial.kt').read_text()
        appearance = (GLASS / 'GlassAppearance.kt').read_text()
        self.assertIn('if (glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier', app)
        self.assertIn('settings.materialMode == MaterialMode.LiquidGlass', app)
        self.assertIn('backdrop.takeIf { enabled }', chrome)
        self.assertIn('Modifier.clip(NavigationBarShape).background(surface)', chrome)
        self.assertIn('rememberGlassGravity(enabled && LocalAppMotionAllowed.current)', appearance)

    def test_settings_errors_are_associated_with_input_and_actions_can_wrap(self):
        controls = (COMMON / 'ui/settings/SettingsControls.kt').read_text()
        components = (COMMON / 'ui/settings/SettingsComponents.kt').read_text()
        reset = (COMMON / 'ui/settings/ResetSettings.kt').read_text()
        self.assertIn('value = draft.value', controls)
        self.assertIn('onValueChange = { edits.input(draft, it) }', controls)
        self.assertIn('SettingsInputMenu {', controls)
        self.assertIn('contentDescription = title; draft.error?.let { error(it) }', controls)
        self.assertIn('SettingsHint(it, isError = true)', controls)
        self.assertIn('liveRegion = LiveRegionMode.Polite', components)
        self.assertIn('FlowRow(', reset)
        self.assertIn('enabled = !state.busy', reset)
        self.assertNotIn('fontScale =', controls)


if __name__ == '__main__':
    unittest.main()
