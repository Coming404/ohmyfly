"""Catalog feedback and global notification layout guards."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'
UI = COMMON / 'ui'


class CatalogFeedbackTests(unittest.TestCase):
    def test_existing_catalog_data_uses_progress_instead_of_skeletons(self):
        towers = (UI / 'TowerLibraryList.kt').read_text()
        nodes = (UI / 'NodesScreen.kt').read_text()
        self.assertIn('if (catalog.summary.towers > 0L) CatalogLoadingIndicator("正在加载塔台")', towers)
        self.assertIn('else CatalogTowerLoading()', towers)
        self.assertIn('if (catalog.summary.flights > 0L) CatalogLoadingIndicator("正在加载航班")', nodes)
        self.assertIn('else CatalogLoading()', nodes)

    def test_empty_states_overlay_the_list_viewport_center(self):
        for name in ('TowerLibraryList.kt', 'NodesScreen.kt'):
            source = (UI / name).read_text()
            list_end = source.index('\n        }\n        if (catalog.')
            empty_overlay = source.index('contentAlignment = Alignment.Center', list_end)
            self.assertLess(list_end, empty_overlay)
            self.assertIn('Modifier.fillMaxSize().padding(', source[list_end:])

    def test_global_messages_clear_the_top_action_toolbar(self):
        source = (UI / 'AppMessages.kt').read_text()
        insets = source.index('.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top')
        message_gap = source.index('.padding(top = 56.dp)', insets)
        self.assertLess(insets, message_gap)

    def test_disclosure_uses_timed_easing_while_navigation_keeps_springs(self):
        disclosure = (UI / 'settings/SettingsExpansion.kt').read_text()
        navigation = (UI / 'AppNavTransition.kt').read_text()
        self.assertIn('tween(200, easing = FastOutSlowInEasing)', disclosure)
        self.assertIn('tween(160, easing = FastOutSlowInEasing)', disclosure)
        self.assertIn('navGraphicsTransition(', navigation)


if __name__ == '__main__':
    unittest.main()
