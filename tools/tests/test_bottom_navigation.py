"""Glass bottom-navigation guards; not Compose rendering or accessibility tests."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'
UI = COMMON / 'ui'
GLASS = UI / 'glass'


class BottomNavigationTests(unittest.TestCase):
    def test_five_existing_destinations_and_single_navigation_owner(self):
        nav = (UI / 'DriftNavigationBar.kt').read_text()
        destinations = (UI / 'AppDestination.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        labels = ['概览', '塔台', '起飞', '航班', '设置']
        positions = [destinations.index(f'("{label}"') for label in labels]
        self.assertEqual(sorted(positions), positions)
        self.assertIn('GlassNavigationBar(', nav)
        self.assertIn('selectedIndex = destination.ordinal', nav)
        self.assertIn('onDestination(AppDestination.entries[it])', nav)
        self.assertIn('onDestination = navigate', app)
        self.assertNotIn('onDestination = { destination = it }', app)
        self.assertIn('!settingsChild && selectionMode == null && !imeVisible', app)

    def test_insets_are_applied_once_outside_the_material(self):
        nav = (UI / 'DriftNavigationBar.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        self.assertEqual(1, nav.count('.windowInsetsPadding('))
        self.assertIn('defaultWindowInsetsPadding = false', nav)
        self.assertIn('.consumeWindowInsets(padding)', app)
        self.assertIn('if (glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier', app)

    def test_navigation_uses_shared_circle_boundary_and_accessible_labels(self):
        nav = (UI / 'DriftNavigationBar.kt').read_text()
        self.assertIn('GlassNavigationItem(it.icon, it.label, it.label)', nav)
        self.assertIn('shape = NavigationBarShape', nav)
        self.assertIn('smoothing = 0f', (UI / 'NavigationBarShape.kt').read_text())
        self.assertFalse((GLASS / 'GlassNavigationSurface.kt').exists())


if __name__ == '__main__':
    unittest.main()
