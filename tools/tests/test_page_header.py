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
        self.assertIn('fontSize = 18.sp', header)
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
        self.assertIn('icon = MiuixIcons.Basic.ArrowRight', source)
        self.assertIn('onClick = onBack', source)
        self.assertIn('scaleX = if (rtl) 1f else -1f', source)
        self.assertIn('TooltipBox(text = label)', source)
        self.assertIn('contentDescription = label', source)
        self.assertIn('defaultMinSize(minWidth = 44.dp, minHeight = 44.dp).clickable(', source)
        self.assertIn('indication = null', source)
        self.assertIn('role = Role.Button', source)
        self.assertIn('Modifier.size(22.dp).then(iconModifier)', source)
        self.assertIn('Modifier.size(38.dp).then(surface()).indication(interaction, LocalIndication.current)', source)
        self.assertIn('val surface = LocalHeaderSurface.current', source)
        self.assertNotIn('IconButton(', source)

    def test_titles_and_back_share_the_existing_navigation_state(self):
        app = (COMMON / 'App.kt').read_text()
        screen = (UI / 'SettingsScreen.kt').read_text()
        self.assertIn('title = if (settingsChild) "" else destination.label', app)
        self.assertIn('if (title.isNotEmpty()) Text(', (UI / 'DriftTopBar.kt').read_text())
        self.assertIn('onBack = settingsBack', app)
        self.assertIn('val settingsPage = settingsStack.last() as SettingsPage', app)
        self.assertIn('mirrorEditorBack?.invoke() ?: settingsStack.popSettingsPage()', app)
        self.assertIn('NavDisplay(', screen)
        self.assertIn('onBack = onBack', screen)
        self.assertNotIn('SettingsBackHandler', app)
        self.assertIn('if (!settingsChild) DriftNavigationBar(', app)
        self.assertIn('contentPadding = padding', app)

    def test_only_real_actions_get_a_surface(self):
        source = (UI / 'DriftTopBar.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        actions = (UI / 'NodeSelectionActions.kt').read_text()
        self.assertIn('actions: (@Composable RowScope.() -> Unit)? = null', source)
        self.assertIn('if (actions != null)', source)
        self.assertIn('content = actions', source)
        self.assertIn('actions = if (destination == AppDestination.NodeLibrary)', app)
        self.assertIn('} else null,', app)
        self.assertIn('enabled = visibleIds.isNotEmpty()', actions)
        self.assertIn('enabled = selectedIds.isNotEmpty()', actions)
        self.assertIn('onSelection(emptySet())', actions)

    def test_safe_area_and_readability_without_resizing_the_content_viewport(self):
        source = (UI / 'DriftTopBar.kt').read_text()
        app = (COMMON / 'App.kt').read_text()
        self.assertIn('WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)', source)
        self.assertIn('padding(horizontal = 16.dp, vertical = 4.dp)', source)
        self.assertIn('heightIn(min = 44.dp)', source)
        self.assertEqual(1, source.count('.windowInsetsPadding('))
        self.assertIn('LocalAppSurfaceColors.current.page', source)
        self.assertIn('Brush.verticalGradient(', source)
        self.assertIn('0.8f to pageColor', source)
        self.assertIn('1f to pageColor.copy(alpha = 0f)', source)
        self.assertIn('Modifier.chromeMaterial(backdrop, glassEnabled, dark, materialMode)', source)
        self.assertIn('.consumeWindowInsets(padding)', app)
        self.assertIn('Modifier.layerBackdrop(backdrop)', app)
        self.assertIn('val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl', source)
        self.assertNotIn('animate', source.casefold())


if __name__ == '__main__':
    unittest.main()
