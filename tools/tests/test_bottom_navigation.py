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
        surface = (GLASS / 'GlassNavigationSurface.kt').read_text()
        tabs = (GLASS / 'GlassNavigationTabs.kt').read_text()
        destinations = (UI / 'AppDestination.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        labels = ['概览', '塔台', '起飞', '航班', '设置']
        positions = [destinations.index(f'("{label}"') for label in labels]
        self.assertEqual(sorted(positions), positions)
        self.assertIn('GlassNavigationSurface(', nav)
        self.assertIn('GlassNavigationTabs(', surface)
        self.assertIn('hapticChange', tabs)
        self.assertIn('onDestination = navigate', app)
        self.assertNotIn('onDestination = { destination = it }', app)
        self.assertIn('!settingsChild && selectionMode == null && !imeVisible', app)

    def test_insets_are_applied_once_and_navigation_uses_material_settings(self):
        nav = (UI / 'DriftNavigationBar.kt').read_text()
        surface = (GLASS / 'GlassNavigationSurface.kt').read_text()
        effects = (GLASS / 'GlassEffects.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        tuning = (UI / 'GlassMaterialTuning.kt').read_text()
        self.assertIn('material = navigationBarMaterial()', nav)
        self.assertIn('Modifier.glassSurface(', surface)
        self.assertIn('blur(material.blurRadius.toPx())', effects)
        self.assertIn('ClearBlurRadiusDp = 0.75f', (GLASS / 'GlassPreset.kt').read_text())
        self.assertIn('blendColors(colors)', effects)
        self.assertIn('clearFace()', effects)
        self.assertIn('settings.secondLayerAlpha ?: 0f', tuning)
        self.assertIn('settings.thirdLayerAlpha ?: 0f', tuning)
        self.assertIn('noiseDither(0f)', effects)
        self.assertEqual(1, nav.count('.windowInsetsPadding('))
        self.assertIn('.consumeWindowInsets(padding)', app)
        self.assertIn('if (glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier', app)

    def test_navigation_restores_legacy_capsule_and_keeps_current_g2_shape(self):
        surface = (GLASS / 'GlassNavigationSurface.kt').read_text()
        tabs = (GLASS / 'GlassNavigationTabs.kt').read_text()
        shape = (UI / 'NavigationBarShape.kt').read_text()
        icons = (UI / 'DriftIcons.kt').read_text()
        tuning = (UI / 'GlassMaterialTuning.kt').read_text()
        shadow = (UI / 'NavigationInnerShadow.kt').read_text()
        self.assertIn('glassNavigationCapsule(', surface)
        self.assertIn('rememberGlassNavigationGesture(', surface)
        self.assertIn('response.drag.modifier', surface)
        self.assertIn('navigationInnerShadow(shape)', surface)
        self.assertIn('NavigationInnerShadow(radius = 8.dp * press', surface)
        self.assertIn('spot?.let { Box(Modifier.matchParentSize().clip(NavigationBarShape).then(it.modifier)) }', surface)
        self.assertIn('smoothing = 1f', shape)
        self.assertIn('item.navigationIcon(selected)', tabs)
        self.assertIn('FontWeight.Bold', tabs)
        self.assertIn('Glass.Medium.', icons)
        self.assertIn('settings.secondLayerAlpha ?: 0f', tuning)
        self.assertIn('settings.thirdLayerAlpha ?: 0f', tuning)
        self.assertIn('BloomStroke', (GLASS / 'GlassEffects.kt').read_text())
        self.assertIn('drawClearBloom(shape, style, it, preset, response.drag.pressProgress)', surface)
        self.assertIn('fontWeight = FontWeight.SemiBold', (UI / 'DriftTopBar.kt').read_text())
        self.assertIn('Modifier.Node', shadow)

    def test_catalog_and_selection_toolbars_share_clear_capsule_metrics(self):
        metrics = (UI / 'ChromeMetrics.kt').read_text()
        toolbar = (UI / 'CatalogActionToolbar.kt').read_text()
        selection = (UI / 'CatalogSelection.kt').read_text()
        tower = (UI / 'TowerCardActions.kt').read_text()
        self.assertIn('val ToolbarButtonSize = 40.dp', metrics)
        self.assertIn('val ToolbarIconSize = 18.dp', metrics)
        self.assertIn('val ToolbarLabelSize = 12.sp', metrics)
        self.assertIn('modifier.wrapContentWidth()', selection)
        self.assertNotIn('SelectionWidth', selection)
        self.assertIn('ChromeMetrics.ToolbarButtonSize', toolbar)
        self.assertIn('fontWeight = FontWeight.SemiBold', selection)
        self.assertIn('ToolbarIconSize', tower)
        self.assertIn('ToolbarLabelSize', tower)
        self.assertIn('ChromeMetrics.ActionGap', tower)


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
        self.assertIn('Modifier.matchParentSize().drawBackdrop(', top)
        self.assertIn('progressiveBlur(blurRadiusDp.dp.toPx()', top)
        self.assertIn('clearFace()', top)
        self.assertIn('ClearBlurRadiusDp', top)
        self.assertIn('glassSurface(', chrome)
        self.assertIn('GlassTopAppBarDefaults.buttonMaterial()', chrome)

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
