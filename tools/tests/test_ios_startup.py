"""iOS wiring guards; compilation and the system permission prompt require native validation."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
IOS = ROOT / 'shared/src/iosMain/kotlin/com/ohmyfly'


class IosStartupTests(unittest.TestCase):
    def test_haptics_uses_objc_factory_instead_of_swift_only_initializer(self):
        source = (IOS / 'ui/AppHaptics.ios.kt').read_text()
        self.assertIn('UIImpactFeedbackGenerator.feedbackGeneratorWithStyle(style, forView = view)', source)
        self.assertNotIn('UIImpactFeedbackGenerator(style = style, view = view)', source)
        self.assertIn('majorVersion = 17', source)
        self.assertIn('minorVersion = 5', source)
        self.assertIn('else UIImpactFeedbackGenerator(style = style)', source)

    def test_network_attempt_is_launched_without_blocking_ui(self):
        entry = (IOS / 'MainViewController.kt').read_text()
        self.assertEqual(1, entry.count('LaunchedEffect(Unit) { requestStartupNetworkAccess() }'))
        self.assertNotIn('runBlocking', entry)
        self.assertIn('App(dynamicColorSeed = null', entry)

    def test_network_attempt_is_bounded_anonymous_and_cancel_safe(self):
        source = (IOS / 'network/StartupNetworkAccess.kt').read_text()
        self.assertIn('client.head(SpeedSettings().latencyUrl)', source)
        self.assertIn('withTimeoutOrNull(10_000L)', source)
        for setting in ('followRedirects = false', 'HTTPShouldSetCookies = false',
                        'HTTPCookieStorage = null', 'URLCredentialStorage = null',
                        'URLCache = null', 'waitsForConnectivity = false'):
            self.assertIn(setting, source)
        self.assertIn('catch (cancelled: CancellationException)', source)
        self.assertIn('throw cancelled', source)
        self.assertRegex(source, r'finally\s*\{\s*client.close\(\)')


if __name__ == '__main__':
    unittest.main()
