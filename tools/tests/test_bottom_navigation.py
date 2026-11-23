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
        destinations = (UI / 'AppDestination.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        labels = ['概览', '塔台', '起飞', '航班', '设置']
        positions = [destinations.index(f'("{label}"') for label in labels]
        self.assertEqual(sorted(positions), positions)
        self.assertIn('GlassNavigationSurface(', nav)
        self.assertIn('destination = destination', nav)
        self.assertIn('onDestination = onDestination', nav)
        self.assertIn('onSelect = { onDestination(AppDestination.entries[it]) }', surface)
        self.assertIn('onDestination = navigate', app)
        self.assertIn('destination = next', app)
        self.assertNotIn('onDestination = { destination = it }', app)
        self.assertRegex(app, r'if\s*\(!settingsChild\s*&&\s*selectionMode\s*==\s*null\s*&&\s*!imeVisible\)\s*DriftNavigationBar\(')
        self.assertIn('backdrop = backdrop', nav)
        self.assertIn('rememberLayerBackdrop()', surface)
        self.assertNotIn('ChromeBackdrop', surface)
        self.assertNotIn('liquidCapsule', surface)
        self.assertFalse((GLASS / 'ChromeBackdrop.kt').exists())
        self.assertFalse((GLASS / 'LiquidSurface.kt').exists())
        self.assertNotIn('FloatingNavigationBar(', surface)

    def test_labels_are_always_below_existing_icons(self):
        tabs = (GLASS / 'GlassNavigationTabs.kt').read_text()
        self.assertIn('Column(', tabs)
        self.assertLess(tabs.index('Icon(item.icon'), tabs.index('Text('))
        self.assertIn('NavigationLabel(item.label, selected, color)', tabs)
        self.assertIn('Modifier.size(22.dp)', tabs)
        self.assertIn('fontSize = 11.sp', tabs)
        self.assertIn('lineHeight = 14.sp', tabs)
        self.assertIn('Arrangement.spacedBy(1.dp, Alignment.CenterVertically)', tabs)
        label = tabs.split('private fun NavigationLabel(', 1)[1]
        self.assertIn('listOf(FontWeight.Normal, FontWeight.Bold).forEach { weight ->', label)
        self.assertIn('fontWeight = weight', label)
        self.assertIn('color = if (selected == (weight == FontWeight.Bold)) color else Color.Transparent', label)
        self.assertNotIn('fontWeight = if', label)
        self.assertNotIn('TooltipBox', tabs)

    def test_equal_touch_regions_and_single_tab_semantics(self):
        tabs = (GLASS / 'GlassNavigationTabs.kt').read_text()
        surface = (GLASS / 'GlassNavigationSurface.kt').read_text()
        self.assertIn('Modifier.weight(1f)', tabs)
        self.assertIn('ChromeMetrics.NavigationHeight else 64.dp', tabs)
        self.assertIn('heightIn(min = minimumHeight)', tabs)
        self.assertNotIn('.height(64.dp)', tabs)
        self.assertIn('.selectableGroup()', surface)
        self.assertIn('.selectable(', tabs)
        self.assertIn('selected = selected', tabs)
        self.assertIn('role = Role.Tab', tabs)
        self.assertIn('contentDescription = null', tabs)
        self.assertIn('if (!recording && pressed) tint.copy(alpha = 0.6f)', tabs)
        self.assertIn('if (recording) Modifier else Modifier', tabs)
        self.assertIn('softWrap = true', tabs)
        self.assertNotIn('maxLines = 1', tabs)
        self.assertNotIn('TextOverflow.Ellipsis', tabs)
        self.assertIn('clearAndSetSemantics { contentDescription = label }', tabs)
        # The recording copy is drawing only: no duplicate semantics or pointer input.
        self.assertIn('clearAndSetSemantics {}', surface)
        self.assertIn('GlassNavigationTabs(destination.ordinal, {}, recording = true)', surface)

    def test_insets_are_applied_once_outside_the_material(self):
        nav = (UI / 'DriftNavigationBar.kt').read_text()
        self.assertIn('val NavigationHeight = 62.dp', (UI / 'ChromeMetrics.kt').read_text())
        self.assertIn('val NavigationBottomInset = 21.dp', (UI / 'ChromeMetrics.kt').read_text())
        self.assertIn('val NavigationIndicatorHeight = 56.dp', (UI / 'ChromeMetrics.kt').read_text())
        self.assertIn('platform() == Platform.IOS', nav)
        self.assertIn('padding(bottom = ChromeMetrics.NavigationBottomInset)', nav)
        self.assertIn('WindowInsetsSides.Bottom', nav)
        app = (COMMON / 'App.kt').read_text()
        self.assertIn('WindowInsetsSides.Horizontal', nav)
        self.assertIn('WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)', nav)
        self.assertEqual(2, nav.count('.windowInsetsPadding('))
        self.assertIn('platform() == Platform.IOS', nav)
        self.assertIn('padding(bottom = ChromeMetrics.NavigationBottomInset)', nav)
        self.assertIn('Modifier.padding(bottom = ChromeMetrics.NavigationBottomInset)', nav)
        self.assertIn('.padding(bottom = 8.dp)', nav)
        self.assertIn('padding(horizontal = 24.dp)', nav)
        self.assertIn('Modifier.widthIn(max = 420.dp).fillMaxWidth()', nav)
        self.assertLess(nav.index('.windowInsetsPadding('), nav.index('GlassNavigationSurface('))
        self.assertIn('.consumeWindowInsets(padding)', app)
        self.assertIn('if (glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier', app)
        self.assertIn('glassEffectsEnabled(reduceTransparency, glassShaderSupported(), systemReduceTransparency)', app)

    def test_tabs_and_capsule_share_one_smooth_boundary(self):
        surface = (GLASS / 'GlassNavigationSurface.kt').read_text()
        ch = (UI / 'ChromeMaterial.kt').read_text()
        shape = (GLASS / 'GlassShape.kt').read_text()
        self.assertIn('NavigationBarShape', surface)
        self.assertIn('val capsuleShape = NavigationBarShape', surface)
        outline = (UI / 'NavigationBarShape.kt').read_text()
        self.assertIn('AppCorners.Capsule', outline)
        self.assertIn('-size.minDimension * 0.5f', outline)
        self.assertNotIn('cornerPoint(', shape)
        self.assertIn('p - clamp(p, -extent, extent)', shape)
        self.assertIn('glassSurface(', surface)
        self.assertIn('glassNavigationCapsule(', surface)
        selection = surface.split('private fun BoxScope.GlassNavigationSelection(', 1)[1].split(
            '/** The combined source', 1,
        )[0]
        self.assertIn('if (enabled) capsule.glassNavigationCapsule(', selection)
        self.assertNotIn('response.drag.pressProgress > 0f) {\n            Box(capsule.glassNavigationCapsule', selection)
        self.assertIn('rememberCombinedBackdrop(backdrop, tabsBackdrop)', surface)
        self.assertIn('.layerBackdrop(tabsBackdrop)', surface)
        self.assertNotIn('liquidCapsule(', surface)
        self.assertNotIn('ChromeBackdrop', surface)
        self.assertIn('rememberGlassHighlight(', surface)
        self.assertIn('rememberGlassNavigationGesture(', surface)
        self.assertIn('InteractiveHighlight', ch + (GLASS / 'InteractiveHighlight.kt').read_text())
        self.assertIn('internal abstract class GlassShape', shape)
        self.assertIn('surface.createOutline(size, layoutDirection, density)', shape)
        self.assertIn('private val ChromeShape = NavigationBarShape', ch)
        self.assertIn('fun tile(', shape)
        for shared in ('rememberAnimatedGlassPreset(materialMode, dark, enabled, motion)', 'glassSurface(', 'val outline = shape'):
            with self.subTest(shared=shared):
                self.assertIn(shared, ch)
        self.assertNotIn('textureBlur(', ch)
        self.assertIn('else {\n        base.background(surface)', ch)


if __name__ == '__main__':
    unittest.main()
