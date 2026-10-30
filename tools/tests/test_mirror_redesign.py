"""Mirror redesign source guards, not native rendering or network execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'shared/src'
UI = SOURCE / 'commonMain/kotlin/com/ohmyfly/ui/settings'


class MirrorRedesignTests(unittest.TestCase):
    def test_empty_state_is_only_icon_and_bold_text(self):
        page = (UI / 'MirrorSettingsPage.kt').read_text()
        empty = page.split('if (mirrors.isEmpty()) item(key = "mirror-empty")', 1)[1].split('if (mirrors.isNotEmpty()) item', 1)[0]
        self.assertIn('MirrorIcons.Github', empty)
        self.assertIn('Text("暂无镜像源"', empty)
        self.assertIn('fontWeight = FontWeight.Bold', empty)
        self.assertNotIn('Button(', empty)
        self.assertNotIn('SettingsCard', empty)
        self.assertNotIn('name = "直连"', page)
        self.assertIn('if (mirrors.isNotEmpty()) MirrorProbeBar', page)

    def test_manual_save_uses_prefix_only_editor(self):
        editor = (UI / 'MirrorEditor.kt').read_text()
        owner = (UI / 'MirrorPageState.kt').read_text()
        dismiss = owner.split('fun dismiss()', 1)[1].split('fun finishDismiss()', 1)[0]
        self.assertNotIn('submit(', dismiss)
        self.assertIn('text = when { readFailed -> "重试读取"; draft.saving > 0 -> "保存中…"; else -> "保存" }', editor)
        self.assertNotIn('onFocusChanged', editor)
        self.assertNotIn('onSave()', editor.split('internal fun MirrorEditorSave', 1)[0])
        self.assertIn('MirrorPrefixRow()', editor)
        self.assertIn('EditorLabel("拼接方式", MirrorIcons.Link)', editor)
        self.assertIn('"HTTP(S) 前缀"', editor)
        self.assertIn('contentDescription = "拼接方式，HTTP(S) 前缀"', editor)
        self.assertNotIn('OverlayDropdownPopup(', editor)
        self.assertNotIn('Role.DropdownList', editor)
        self.assertNotIn('MirrorIcons.ChevronDown', editor)
        self.assertIn('background(inputColors.backgroundColor', editor)
        self.assertNotIn('chromeMaterial', editor)
        self.assertIn('session.changeConfirmed', owner)
        self.assertIn('mirrorEnabled = current.mirrorEnabled && remaining.isNotEmpty()', owner)
        self.assertIn('OverlayIconDropdownMenu(', (UI / 'MirrorRow.kt').read_text())
        self.assertNotIn('上移', (UI / 'MirrorRow.kt').read_text())
        self.assertNotIn('下移', (UI / 'MirrorRow.kt').read_text())
        dialog = (UI / 'MirrorDialog.kt').read_text()
        self.assertNotIn('chromeMaterial', dialog)
        self.assertIn('enableWindowDim = true', dialog)
        self.assertIn('Modifier.shadow(18.dp', dialog)

    def test_editor_labels_and_prefix_value_share_foreground_typography(self):
        editor = (UI / 'MirrorEditor.kt').read_text()
        for text, icon in (("名称", "Name"), ("拼接方式", "Link"), ("地址", "Globe"), ("改写预览", "External")):
            self.assertIn(f'EditorLabel("{text}", MirrorIcons.{icon})', editor)
        label = editor.split('private fun EditorLabel(', 1)[1].split('\n@Composable', 1)[0]
        prefix = editor.split('private fun MirrorPrefixRow()', 1)[1].split('\n@Composable', 1)[0]
        self.assertIn('Text(text, fontSize = 13.sp, fontWeight = FontWeight.Normal,', label)
        self.assertIn('color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface)', label)
        self.assertRegex(prefix, r'"HTTP\(S\) 前缀",\s+fontSize = 13.sp,\s+fontWeight = FontWeight.Normal,\s+color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface,')
        self.assertIn('onSurfaceVariantSummary', label)
        self.assertIn('tint = iconColor, modifier = Modifier.size(16.dp)', label)
        self.assertIn('Arrangement.spacedBy(8.dp)', label)
        self.assertNotIn('Color.Black', editor)
        self.assertEqual(2, editor.count('main.copy(fontSize = 14.sp)'))
        preview = editor.split('SelectionContainer {', 1)[1].split('draft.failure', 1)[0]
        self.assertIn('fontSize = 12.sp', preview)

    def test_latency_sort_selection_and_results_live_above_page_route(self):
        page = (UI / 'MirrorSettingsPage.kt').read_text()
        runner = (UI / 'MirrorProbeState.kt').read_text()
        row = (UI / 'MirrorRow.kt').read_text()
        app = (SOURCE / 'commonMain/kotlin/com/ohmyfly/App.kt').read_text()
        self.assertIn('sortMirrors(mirrors, runner.results)', page)
        self.assertIn('consumeFastestMirrorId()', page)
        self.assertIn('runner.syncMirrors(mirrors)', page)
        self.assertIn('MirrorProbeRunner(MirrorProbe(createMirrorTransport()), navScope)', app)
        self.assertNotIn('runner.stop()', page)
        self.assertIn('mirrorLatencyBand(latency)', row)
        self.assertIn('MirrorLatencyBand.Fast', runner)
        self.assertIn('MirrorLatencyBand.Moderate', runner)
        self.assertIn('MirrorLatencyBand.Slow', runner)
        self.assertIn('springAnimateToPage(targetPage)', app)
        self.assertIn('else pagerState.scrollToPage(targetPage)', app)

    def test_disabled_controls_and_empty_list_enable(self):
        page = (UI / 'MirrorSettingsPage.kt').read_text()
        app = (SOURCE / 'commonMain/kotlin/com/ohmyfly/App.kt').read_text()
        codec = (SOURCE / 'commonMain/kotlin/com/ohmyfly/settings/MirrorCodec.kt').read_text()
        actions = (UI / 'MirrorPageActions.kt').read_text()
        self.assertIn('enabled = !session.state.readFailed', page)
        self.assertIn('MirrorDisabledVeil(!settings.mirrorEnabled || session.state.readFailed)', page)
        self.assertIn('settings.mirrorEnabled && !settingsSession.state.readFailed', app)
        self.assertIn('enabled = settings.mirrorEnabled && !session.state.readFailed', page)
        self.assertNotIn('enabled && mirrors.isNotEmpty()', codec)
        self.assertNotIn('MirrorIcons.Sort', page)
        self.assertIn('fontSize = 18.sp, fontWeight = FontWeight.SemiBold', page)
        self.assertNotIn('重试失败项', actions)
        self.assertIn('Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)', actions)

    def test_ios_menu_uses_platform_default_toolbar_with_input_scope(self):
        ios = (SOURCE / 'iosMain/kotlin/com/ohmyfly/ui/settings/SettingsInputMenu.ios.kt').read_text()
        android = (SOURCE / 'androidMain/kotlin/com/ohmyfly/ui/settings/SettingsInputMenu.android.kt').read_text()
        self.assertIn('internal actual fun SettingsInputMenu(content: @Composable () -> Unit) = content()', ios)
        self.assertNotIn('HiddenSettingsTextToolbar', ios)
        self.assertNotIn('LocalTextToolbar', ios)
        self.assertNotIn('TextToolbarStatus.Hidden', ios)
        self.assertIn('= content()', android)
        self.assertEqual(2, (UI / 'MirrorEditor.kt').read_text().count('SettingsInputMenu {'))
        self.assertIn('SettingsInputMenu {', (UI / 'SettingsControls.kt').read_text())
        self.assertNotIn('LocalTextToolbar', (ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly/App.kt').read_text())


if __name__ == '__main__':
    unittest.main()
