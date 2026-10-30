"""Startup recorder wiring guards; platform compilation and frame timing need devices."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / 'shared/src'
COMMON = SHARED / 'commonMain/kotlin/com/ohmyfly'
ANDROID = SHARED / 'androidMain/kotlin/com/ohmyfly/startup/StartupRecording.android.kt'
IOS = SHARED / 'iosMain/kotlin/com/ohmyfly/startup/StartupRecording.ios.kt'
NS = '{http://schemas.android.com/apk/res/android}'


class StartupRecordingTests(unittest.TestCase):
    def test_twenty_second_window_bounds_both_timer_and_frame_collection(self):
        summary = (COMMON / 'startup/StartupSummary.kt').read_text()
        record = (COMMON / 'startup/StartupRecord.kt').read_text()
        sampling = (COMMON / 'startup/StartupRecording.kt').read_text()
        self.assertRegex(summary, r'STARTUP_WINDOW_MILLIS\s*=\s*20_000L')
        self.assertIn('device.entryUptimeMillis + STARTUP_WINDOW_MILLIS', record)
        self.assertRegex(record, r'delay\(record.remainingMillis\(\)\)\s*record.stop\(\)')
        self.assertRegex(record, r'if \(!recording \|\| now >= deadlineMillis \|\| frameCount == capacity\) return')
        self.assertIn('withTimeoutOrNull(remaining)', sampling)
        self.assertRegex(sampling, r'while \(record.recording\).*withFrameNanos')
        self.assertIn('withContext(Dispatchers.IO)', record)
        self.assertIn('fs.atomicMove(temporary, path)', record)

    def test_retention_keeps_ten_complete_sections(self):
        summary = (COMMON / 'startup/StartupSummary.kt').read_text()
        self.assertRegex(summary, r'MAX_STARTUP_RECORDS\s*=\s*10\b')
        self.assertIn('split(STARTUP_RECORD_HEADER)', summary)
        self.assertIn('takeLast(MAX_STARTUP_RECORDS)', summary)
        self.assertTrue((SHARED / 'commonTest/kotlin/com/ohmyfly/startup/StartupSummaryTest.kt').is_file())

    def test_export_is_in_first_about_card_and_uses_unified_errors(self):
        about = (COMMON / 'ui/settings/AboutSettingsPage.kt').read_text()
        first_card = about.split('item { SettingsCard {', 1)[1].split('} }', 1)[0]
        self.assertRegex(first_card, r'SettingsActionRow\("导出启动记录",\s*SettingsIcons\.\w+\)')
        self.assertIn('exportStartupRecords()', first_card)
        sampling = (COMMON / 'startup/StartupRecording.kt').read_text()
        self.assertIn('LocalAppMessages.current', sampling)
        self.assertIn('AppMessageKind.Error', sampling)
        self.assertIn('启动记录文件不存在', sampling)

    def test_platform_actuals_and_native_entry_calls_are_present(self):
        common = (COMMON / 'startup/StartupRecording.kt').read_text()
        self.assertIn('expect fun rememberStartupShare()', common)
        for path in (ANDROID, IOS):
            source = path.read_text()
            self.assertIn('actual fun rememberStartupShare()', source)
            self.assertIn('actual fun startupUptimeMillis()', source)
        android = ANDROID.read_text()
        self.assertIn('Process.getStartUptimeMillis()', android)
        self.assertIn('display.refreshRate', android)
        self.assertIn('Intent.ACTION_SEND', android)
        self.assertIn('FLAG_GRANT_READ_URI_PERMISSION', android)
        ios = IOS.read_text()
        for api in ('sysctl(', 'UIActivityViewController(', 'maximumFramesPerSecond',
                    'keyWindow', 'presentedViewController', 'sourceView ='):
            self.assertIn(api, ios)
        self.assertIn('iosSettingsPath().toPath().parent!!', ios)
        self.assertIn('recordAndroidStartupEntry(this)',
                      (ROOT / 'androidApp/src/main/kotlin/com/ohmyfly/app/MainActivity.kt').read_text())
        self.assertIn('recordIosStartupEntry()',
                      (SHARED / 'iosMain/kotlin/com/ohmyfly/MainViewController.kt').read_text())
        app = (COMMON / 'App.kt').read_text()
        for call in ('StartupRecords.appComposed(version)', 'StartupFrameRecording(startupRecord)',
                     'StartupSettingsRecording(startupRecord, session, settings, dark)', 'startupFirstDraw()'):
            self.assertIn(call, app)

    def test_android_provider_is_private_and_only_exposes_log_directory(self):
        manifest = ET.parse(ROOT / 'androidApp/src/main/AndroidManifest.xml').getroot()
        provider = next(p for p in manifest.findall('application/provider')
                        if p.get(NS + 'name') == '.StartupRecordProvider')
        provider_source = (ROOT / 'androidApp/src/main/kotlin/com/ohmyfly/app/StartupRecordProvider.kt').read_text()
        self.assertIn('StartupRecordProvider : FileProvider()', provider_source)
        self.assertEqual('${applicationId}.startup-records', provider.get(NS + 'authorities'))
        self.assertEqual('false', provider.get(NS + 'exported'))
        self.assertEqual('true', provider.get(NS + 'grantUriPermissions'))
        metadata = provider.find('meta-data')
        self.assertEqual('android.support.FILE_PROVIDER_PATHS', metadata.get(NS + 'name'))
        self.assertEqual('@xml/startup_record_paths', metadata.get(NS + 'resource'))
        paths = ET.parse(ROOT / 'androidApp/src/main/res/xml/startup_record_paths.xml').getroot()
        self.assertEqual([('files-path', 'startup/')], [(p.tag, p.get('path')) for p in paths])


if __name__ == '__main__':
    unittest.main()
