"""日志文件浏览接线守卫；真实分页与竞态由 Kotlin 回归执行。"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'


class LogFilesTests(unittest.TestCase):
    def test_file_reads_stay_on_existing_serial_io_worker(self):
        app = (COMMON / 'logging/AppLog.kt').read_text()
        worker = (COMMON / 'logging/LogWorker.kt').read_text()
        store = (COMMON / 'logging/FileLogStore.kt').read_text()
        reader = (COMMON / 'logging/LogFileScan.kt').read_text()
        self.assertIn('SupervisorJob() + Dispatchers.IO', app)
        self.assertIn('request(Request(Action.Files))', worker)
        self.assertIn('request(Request(Action.FilePage', worker)
        self.assertIn('info.generation != generation', store)
        self.assertIn('file.copy(bytes = info.bytes)', store)
        self.assertIn('scanned < PAGE_SCAN_BYTES', reader)
        self.assertIn('entries.size < 200', reader)
        self.assertIn('ByteArray(MAX_RECORD_BYTES)', reader)
        self.assertNotIn('readUtf8()', reader)

    def test_file_card_replaces_record_cards_and_manual_pagination(self):
        page = (COMMON / 'ui/settings/LogsSettingsPage.kt').read_text()
        text = (COMMON / 'ui/settings/LogFileContent.kt').read_text()
        self.assertEqual(1, page.count('SettingsCard {'))
        self.assertIn('state.files.forEachIndexed', page)
        self.assertIn('key(file.id)', page)
        self.assertIn('SettingsExpansion(expanded)', page)
        self.assertIn('LogFileContent(state, file.id)', page)
        self.assertIn('if (!active) return@LaunchedEffect', text)
        self.assertIn('state.files.isEmpty() ||', page)
        row = (COMMON / 'ui/settings/LogFileRow.kt').read_text()
        self.assertIn('SettingsIcons.File', row)
        self.assertIn('fontSize = 15.sp', row)
        self.assertIn('fontSize = 12.sp', row)
        self.assertIn('maxLines = 1', row)
        for old in ('LogRecord(', '较早', '较新'):
            self.assertNotIn(old, page)
        self.assertIn('snapshotFlow', text)
        self.assertIn('direction.request(', text)
        self.assertIn('source == NestedScrollSource.UserInput', text)
        self.assertIn('heightIn(min = 96.dp, max = maxHeight)', text)
        self.assertNotIn('height(360.dp)', text)
        self.assertIn('state.more(', text)
        self.assertIn('fontSize = 14.sp', text)
        self.assertIn('fontSize = 12.sp', text)
        self.assertIn('FontWeight.Normal', text)

    def test_only_single_file_scan_remains(self):
        self.assertFalse((COMMON / 'logging/LogReader.kt').exists())
        models = (COMMON / 'logging/LogModels.kt').read_text()
        worker = (COMMON / 'logging/LogWorker.kt').read_text()
        app = (COMMON / 'logging/AppLog.kt').read_text()
        self.assertNotIn('class LogCursor', models)
        self.assertNotIn('class LogPage(', models)
        self.assertNotIn('Action.Page', worker)
        self.assertNotIn('suspend fun page(', app)

    def test_clear_copy_and_policy_reuse_existing_feedback(self):
        actions = (COMMON / 'ui/settings/LogActions.kt').read_text()
        text = (COMMON / 'ui/settings/LogFileContent.kt').read_text()
        policy = (COMMON / 'ui/settings/LogPolicyCard.kt').read_text()
        self.assertIn('SettingsInfo("记录级别", "", SettingsIcons.Filter, value = LogLevel.Detail.label)', policy)
        self.assertNotIn('ChoiceSetting("记录级别"', policy)
        self.assertFalse((COMMON / 'ui/settings/LogLevelMenu.kt').exists())
        self.assertNotIn('LogLevelMenu', text)
        self.assertIn('Box(Modifier.weight(1f))', text)
        self.assertIn('LogCopyButton(state, entries, active)', text)
        self.assertIn('controlSize = 34.dp, iconSize = 16.dp', text)
        self.assertNotIn('Row(verticalAlignment = Alignment.CenterVertically) {\n            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))', text)
        self.assertIn('topPadding = 0.dp', actions)
        self.assertIn('Row(verticalAlignment = Alignment.CenterVertically)', actions)
        self.assertFalse((COMMON / 'ui/settings/LogLevelSegments.kt').exists())
        app = (COMMON / 'AppTopBar.kt').read_text()
        self.assertIn('MirrorDialog(', actions)
        self.assertIn('if (state.clear()) confirming = false', actions)
        self.assertIn('LocalAppMessages.current', text)
        self.assertIn('copyLogText(text)', text)
        self.assertIn('AppMessageKind.Success', text)
        self.assertIn('AppMessageKind.Failure', text)
        inline = (COMMON / 'ui/InlineChromeIconButton.kt').read_text()
        self.assertIn('InlineChromeIconButton(label, icon, onClick', text)
        self.assertIn('contentDescription = label', inline)
        self.assertNotIn('Text("复制', text)
        self.assertIn('Modifier.size(ChromeMetrics.ControlSize)', inline)
        self.assertIn('hapticClick(onClick)', inline)
        self.assertIn('SettingsExpansion(expanded)', policy)
        self.assertIn('TextSetting(', policy)
        log_action = app.split('logsPage -> {', 1)[1].split('else -> null', 1)[0]
        self.assertIn('HeaderAction("清理全部日志"', log_action)
        self.assertIn('logClear?.invoke()', log_action)
        self.assertIn('MiuixTheme.colorScheme.error', app)
        self.assertIn('日志文件 ${storage.files} 个 ${logBytes(storage.bytes)}', actions)
        self.assertNotIn('导入', actions)


if __name__ == '__main__':
    unittest.main()
