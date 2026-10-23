"""Static boundary checks only; these do not execute Kotlin or mobile networking."""
import base64
import hashlib
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'


class SettingsBoundaryTests(unittest.TestCase):
    def test_probe_fixture_matches_pinned_public_digest(self):
        target = (COMMON / 'mirror/MirrorProbeTarget.kt').read_text()
        fixture = (ROOT / 'shared/src/commonTest/kotlin/com/ohmyfly/mirror/MirrorSample.kt').read_text()
        encoded = ''.join(re.findall(r'"([A-Za-z0-9+/=]+)"', fixture.split('private const val BASE64 =', 1)[1]))
        body = base64.b64decode(encoded, validate=True)
        self.assertEqual(425, len(body))
        expected = re.search(r'const val sha256 = "([a-f0-9]{64})"', target).group(1)
        self.assertEqual(expected, hashlib.sha256(body).hexdigest())
        self.assertNotIn('BASE64', target)

    def test_runtime_kotlin_files_remain_bounded(self):
        sources = subprocess.check_output(['rg', '--files', str(ROOT / 'shared/src'), '-g', '*.kt'], text=True)
        for name in sources.splitlines():
            path = Path(name)
            if 'commonTest' in path.parts:
                continue
            with self.subTest(path=path.relative_to(ROOT)):
                self.assertLessEqual(len(path.read_text().splitlines()), 500)

    def test_platform_transports_do_not_duplicate_jvm_file_facades(self):
        self.assertTrue((COMMON / 'mirror/MirrorTransport.kt').is_file())
        for source, suffix in [('androidMain', 'android'), ('iosMain', 'ios')]:
            directory = ROOT / f'shared/src/{source}/kotlin/com/ohmyfly/mirror'
            self.assertTrue((directory / f'MirrorTransport.{suffix}.kt').is_file())
            self.assertFalse((directory / 'MirrorTransport.kt').exists())

    def test_darwin_dependency_cannot_be_silently_skipped(self):
        build = (ROOT / 'shared/build.gradle.kts').read_text()
        self.assertNotIn('findByName("iosMain")?.dependencies', build)
        self.assertIn('iosMain.dependencies {', build)
        self.assertIn('implementation(libs.ktor.client.darwin)', build)

    def test_old_appearance_page_does_not_survive_as_second_implementation(self):
        current = (COMMON / 'ui/SettingsScreen.kt').read_text()
        home = (COMMON / 'ui/settings/SettingsHome.kt').read_text()
        pages = (COMMON / 'ui/settings/SettingsPage.kt').read_text()
        self.assertIn('SettingsPage.Mirrors', current)
        self.assertIn('SettingsPage.Media', current)
        self.assertIn('SettingsPage.Home -> SettingsHome(settings, onChange, onPage, version', current)
        self.assertIn('ChoiceSetting("明暗模式"', home)
        self.assertIn('it.copy(themeMode = ThemeMode.valueOf(name))', home)
        self.assertIn('it.copy(reduceTransparency = enabled)', home)
        self.assertNotIn('SettingsPage.Appearance', current)
        self.assertNotIn('Appearance(', pages)
        self.assertFalse((COMMON / 'ui/AppearanceSettingsPage.kt').exists())


if __name__ == '__main__':
    unittest.main()
