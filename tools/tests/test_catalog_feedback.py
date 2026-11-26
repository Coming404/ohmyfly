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
        self.assertIn('CatalogLoadingIndicator("正在加载塔台", Modifier.fillParentMaxSize())', towers)
        self.assertIn('else if (catalog.summary.towers == 0L) CatalogTowerLoading()', towers)
        self.assertIn('CatalogLoadingIndicator("正在加载航班", Modifier.fillParentMaxSize())', nodes)
        self.assertIn('else if (catalog.summary.flights == 0L) CatalogLoading()', nodes)

    def test_empty_states_overlay_the_list_viewport_center(self):
        for name in ('TowerLibraryList.kt', 'NodesScreen.kt'):
            source = (UI / name).read_text()
            list_end = source.index('\n        }\n        if (catalog.')
            empty_overlay = source.index('contentAlignment = Alignment.Center', list_end)
            self.assertLess(list_end, empty_overlay)
            self.assertIn('Modifier.fillMaxSize().padding(', source[list_end:])

    def test_tower_sync_shows_per_tower_counts_and_independent_stop(self):
        contract = (ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly/catalog/CatalogContract.kt').read_text()
        batch = (ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly/catalog/CatalogBatchRunner.kt').read_text()
        store = (ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly/catalog/CatalogStoreSync.kt').read_text()
        card = (UI / 'TowerCard.kt').read_text()
        footer = (UI / 'TowerCardActions.kt').read_text()
        self.assertIn('towerTaskProgress: Map<Long, CatalogTaskProgress>', contract)
        self.assertIn('" ${it.completed} / ${it.total}"', footer)
        self.assertIn('progress = catalog.towerTaskProgress[tower.id]', (UI / 'TowerLibraryList.kt').read_text())
        self.assertIn('if (loading) "停止"', footer)
        self.assertIn('store.cancelTowerSync(tower.id)', (UI / 'TowerLibraryList.kt').read_text())
        self.assertIn('fun CatalogStore.cancelTowerSync(id: Long)', store)
        self.assertIn('fun cancelTower(parentId: Long)', batch)
        self.assertNotIn('正在同步", fontSize = 13.sp', card)

    def test_details_and_catalog_loaders_do_not_show_unrelated_card_spinners(self):
        details = (UI / 'FlightDetails.kt').read_text()
        loader = (UI / 'CatalogLoading.kt').read_text()
        self.assertIn('if (showHistory && loading) CircularProgressIndicator()', details)
        self.assertIn('contentAlignment = Alignment.Center', loader)
        indicator = loader.split('internal fun CatalogLoadingIndicator', 1)[1].split('@Composable', 1)[0]
        self.assertNotIn('Card(', indicator)

    def test_flight_filter_refresh_preserves_existing_results(self):
        store = (ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly/catalog/CatalogStore.kt').read_text()
        self.assertIn('preserveResults = preserveResults', store)
        self.assertIn('if (preserveResults) current.copy(filterToken = 0, flightLoading = false', store)

    def test_glass_profiles_match_upstream_and_navigation_text_stays_flat(self):
        profiles = (ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly/settings/GlassLensProfile.kt').read_text()
        nav = (UI / 'glass/GlassNavigationSurface.kt').read_text()
        self.assertIn('Pill("pill", 10f, 38f, 0.2f, 0.25f, 0.03f, 0.618f)', profiles)
        self.assertIn('val navigationPreset = preset.copy(convexLensEnabled = false)', nav)
        self.assertNotIn('glassNavigationCapsule', nav)

    def test_large_subscription_body_is_decoded_once(self):
        fetcher = (COMMON / 'subscription/SubscriptionFetcher.kt').read_text()
        self.assertEqual(1, fetcher.count('bytes.decodeToString()'))
        self.assertIn('parseSubscriptionName(\n                    response.headers["profile-title"]', fetcher)
        self.assertIn('response.headers[HttpHeaders.ContentDisposition], body', fetcher)

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
