"""Header wiring/layout guards; these do not compile or render Compose."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'
UI = COMMON / 'ui'


class PageHeaderTests(unittest.TestCase):
    def test_one_compact_title_without_brand_or_large_title(self):
        source = (UI / 'DriftTopBar.kt').read_text()
        header = source.split('internal fun HeaderAction(', 1)[0]
        self.assertIn('fontSize = ChromeMetrics.TitleSize', header)
        metrics = (UI / 'ChromeMetrics.kt').read_text()
        self.assertIn('TitleSize = 17.sp', metrics)
        self.assertIn('ControlSize = 44.dp', metrics)
        self.assertIn('fontWeight = FontWeight.SemiBold', header)
        self.assertIn('semantics { heading() }', header)
        self.assertIn('maxLines = 1', header)
        self.assertIn('overflow = TextOverflow.Ellipsis', header)
        self.assertNotIn('OhMyFly"', header)
        self.assertNotIn('DriftIcons.Wave', header)
        self.assertNotIn('Column(', header)
        self.assertNotIn('28.sp', header)
        self.assertNotIn('TextButton', source)
        nodes = (UI / 'NodesScreen.kt').read_text()
        self.assertNotIn('SmallTitle("航班")', nodes)

    def test_back_is_icon_only_with_rtl_and_accessible_touch_target(self):
        source = (UI / 'DriftTopBar.kt').read_text()
        self.assertIn('if (onBack != null)', source)
        self.assertIn('label = "返回"', source)
        self.assertIn('icon = MiuixIcons.Glass.ChevronForward', source)
        self.assertIn('onClick = onBack', source)
        self.assertIn('scaleX = if (rtl) 1f else -1f', source)
        self.assertIn('TooltipBox(text = label)', source)
        self.assertIn('contentDescription = label', source)
        self.assertIn('defaultMinSize(minWidth = ChromeMetrics.ControlSize, minHeight = ChromeMetrics.ControlSize)', source)
        self.assertIn('.glassPress(interaction, enabled && LocalAppMotionAllowed.current)', source)
        self.assertIn('indication = null', source)
        self.assertIn('role = Role.Button', source)
        self.assertIn('Modifier.size(ChromeMetrics.ToolbarIconSize).then(iconModifier)', source)
        self.assertIn('Modifier.size(ChromeMetrics.ToolbarButtonSize).then(surface()).indication(interaction, LocalIndication.current)', source)
        self.assertIn('val surface = LocalHeaderSurface.current', source)
        self.assertNotIn('IconButton(', source)

    def test_titles_and_back_share_the_existing_navigation_state(self):
        app = (COMMON / 'App.kt').read_text()
        topbar = (COMMON / 'AppTopBar.kt').read_text()
        screen = (UI / 'SettingsScreen.kt').read_text()
        title = topbar.split('DriftTopBar(', 1)[1].split('backdrop = backdrop', 1)[0]
        self.assertIn('title = if (selectionMode != null)', title)
        self.assertIn('CatalogSelectionMode.Nodes -> selectedIds.size.toLong()', title)
        self.assertIn('CatalogSelectionMode.Towers -> selectedTowerIds.size.toLong()', title)
        self.assertIn('titleCapsule = selectionMode != null', title)
        self.assertIn('settingsChild || destination == AppDestination.Settings', title)
        self.assertNotIn('destination in listOf(', title)
        self.assertNotIn('34.sp', (UI / 'CatalogSearch.kt').read_text())
        self.assertIn('"" else destination.label', title)
        home = (UI / 'settings/SettingsHome.kt').read_text()
        title_item = home.split('SettingsList(padding, state) {', 1)[1].split('item(key = "identity")', 1)[0]
        self.assertIn('item(key = "title")', title_item)
        self.assertIn('SettingsPage.Home.title', title_item)
        self.assertIn('fontSize = 20.sp', title_item)
        self.assertNotIn('stickyHeader', home)
        self.assertIn('if (title.isNotEmpty()) Box(', (UI / 'DriftTopBar.kt').read_text())
        self.assertIn('onBack = settingsBack', topbar)
        self.assertIn('val settingsPage = settingsStack.last() as SettingsPage', app)
        self.assertIn('mirrorEditorBack?.invoke() ?: settingsStack.popSettingsPage()', app)
        self.assertIn('NavDisplay(', screen)
        self.assertIn('onBack = onBack', screen)
        self.assertNotIn('SettingsBackHandler', app)
        self.assertRegex(app, r'if\s*\(!settingsChild\s*&&\s*selectionMode\s*==\s*null\s*&&\s*!imeVisible\)\s*DriftNavigationBar\(')
        self.assertIn('contentPadding = padding', app)

    def test_only_real_actions_get_a_surface(self):
        source = (UI / 'DriftTopBar.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        self.assertIn('actions: (@Composable RowScope.() -> Unit)? = null', source)
        self.assertIn('if (actions != null)', source)
        self.assertIn('content = actions', source)
        actions = (COMMON / 'AppTopBar.kt').read_text().split('actions = when {', 1)[1]
        self.assertIn('selectionMode != null ->', actions)
        self.assertIn('CatalogSelectionTopToolbar(', actions)
        self.assertIn('onCancel = onCancelSelection, onSelectAll = onSelectAll', actions)
        toolbar = (UI / 'CatalogSelection.kt').read_text()
        top_toolbar = toolbar.split('internal fun CatalogSelectionTopToolbar(', 1)[1]
        self.assertIn('CatalogActionToolbar(modifier)', top_toolbar)
        self.assertIn('FloatingToolbar(', (UI / 'CatalogActionToolbar.kt').read_text())
        self.assertIn('SelectionTopAction(true, "取消"', top_toolbar)
        self.assertIn('selectAllLabel', top_toolbar)
        for page in ('NodeLibrary', 'Subscriptions'):
            self.assertIn(f'destination == AppDestination.{page} ->', actions)
        self.assertIn('mirrorPage ->', actions)
        self.assertIn('logsPage ->', actions)
        self.assertIn('else -> null', actions)

    def test_toolbar_press_feedback_is_clipped_to_matching_squircle(self):
        toolbar = (UI / 'CatalogActionToolbar.kt').read_text()
        selection = (UI / 'CatalogSelection.kt').read_text()
        tower = (UI / 'TowerCardActions.kt').read_text()
        self.assertIn('Modifier.size(ChromeMetrics.ToolbarButtonSize).clip(AppCorners.shape(AppCorners.Toolbar))', toolbar)
        self.assertIn('.clip(AppCorners.shape(AppCorners.Toolbar))', selection)
        self.assertIn('Modifier.clip(AppCorners.shape(AppCorners.Toolbar))', selection)
        self.assertIn('cornerRadius = AppCorners.Toolbar', selection)
        self.assertIn('modifier.wrapContentWidth().clip(AppCorners.shape(AppCorners.Control))', tower)
        self.assertIn('modifier.wrapContentWidth().clip(AppCorners.shape(AppCorners.Control))', tower)

    def test_safe_area_and_readability_without_resizing_the_content_viewport(self):
        source = (UI / 'DriftTopBar.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        self.assertIn('WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)', source)
        self.assertIn('padding(horizontal = 16.dp, vertical = 4.dp)', source)
        self.assertIn('heightIn(min = 44.dp)', source)
        self.assertEqual(1, source.count('.windowInsetsPadding('))
        self.assertIn('title.isEmpty() && onBack == null && actions == null', source)
        self.assertIn('Spacer(Modifier.fillMaxWidth().then(safeArea))', source)
        self.assertNotIn('drawBehind', source)
        self.assertNotIn('drawRect', source)
        self.assertNotIn('StatusBarFade', source)
        self.assertNotIn('pageColor', source)
        self.assertIn('Modifier.chromeMaterial(backdrop, glassEnabled, dark)', source)
        self.assertIn('.consumeWindowInsets(padding)', app)
        self.assertIn('if (glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier', app)
        self.assertIn('val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl', source)
        self.assertNotIn('animate', source.casefold())


if __name__ == '__main__':
    unittest.main()
