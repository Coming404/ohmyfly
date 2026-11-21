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
        self.assertIn('NavigationBarContents(', nav)
        self.assertIn('onDestination = hapticChange', nav)
        self.assertIn('onDestination = navigate', app)
        self.assertNotIn('onDestination = { destination = it }', app)
        self.assertIn('!settingsChild && selectionMode == null && !imeVisible', app)

    def test_insets_are_applied_once_outside_the_material(self):
        nav = (UI / 'DriftNavigationBar.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        self.assertIn('shading = glassEnabled', nav)
        self.assertIn('GlassNavigationBarDefaults.stroke()', nav)
        self.assertIn('navigationBarMaterial()', nav)
        self.assertEqual(1, nav.count('.windowInsetsPadding('))
        self.assertIn('.consumeWindowInsets(padding)', app)
        self.assertIn('if (glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier', app)

    def test_navigation_uses_g2_glass_and_selected_theme_weight(self):
        nav = (UI / 'DriftNavigationBar.kt').read_text()
        shape = (UI / 'NavigationBarShape.kt').read_text()
        icons = (UI / 'DriftIcons.kt').read_text()
        material = (UI / 'NavigationBarMaterial.kt').read_text()
        drag = (UI / 'NavigationBarDrag.kt').read_text()
        self.assertIn('NavigationBarItem(', nav)
        self.assertIn('shape = NavigationBarShape', nav)
        self.assertIn('smoothing = 1f', shape)
        self.assertIn('FontWeight.SemiBold', nav)
        self.assertIn('colors.primary', nav)
        self.assertIn('Glass.Medium.', icons)
        self.assertIn('NavigationSecondLayerAlpha = 0.34f', material)
        self.assertIn('NavigationThirdLayerAlpha = 0.18f', material)
        self.assertIn('navigationPosition(', drag)
        self.assertFalse((GLASS / 'GlassNavigationSurface.kt').exists())

    def test_g2_is_the_app_wide_default_for_miuix_and_settings_cards(self):
        theme = (UI / 'AppTheme.kt').read_text()
        settings = (UI / 'settings/SettingsControls.kt').read_text()
        corners = (ROOT / 'ui-shapes/src/commonMain/kotlin/com/ohmyfly/shapes/AppCorners.kt').read_text()
        self.assertIn('LocalSquircleEnabled provides true', theme)
        self.assertIn('val SettingsCard = Card', corners)
        self.assertIn('squircleBorder(', settings)
        self.assertIn('cornerRadius = com.ohmyfly.shapes.AppCorners.SettingsCard', settings)

    def test_top_glass_uses_bounded_progressive_blur_and_refracts(self):
        top = (UI / 'DriftTopBar.kt').read_text()
        chrome = (UI / 'ChromeMaterial.kt').read_text()
        self.assertIn('Modifier.matchParentSize().progressiveTextureBlur(', top)
        self.assertIn('shape = RectangleShape', top)
        self.assertIn('GlassTopAppBarDefaults.ProgressiveBlurRadius.value', top)
        self.assertIn('shading = enabled', chrome)
        self.assertIn('NavigationBarShape', chrome)

    def test_selection_actions_use_theme_foreground_and_group_add_is_unfilled(self):
        selection = (UI / 'CatalogSelection.kt').read_text()
        groups = (UI / 'FlightGroups.kt').read_text()
        self.assertIn('MiuixTheme.colorScheme.onSurface else', selection)
        self.assertIn('backgroundColor = Color.Transparent', groups)
        self.assertIn('Modifier.size(18.dp)', groups)
        self.assertNotIn('InlineChromeIconButton("新建分组"', groups)

    def test_click_feedback_is_clipped_to_chip_and_card_shapes(self):
        groups = (UI / 'FlightGroups.kt').read_text()
        control = (UI / 'TowerControl.kt').read_text()
        self.assertIn('.clip(shape)\n        .clickable(role = Role.Tab', groups)
        self.assertIn('.clip(AppCorners.shape(AppCorners.Card)).clickable', control)



if __name__ == '__main__':
    unittest.main()
