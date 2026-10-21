"""Mirror redesign source guards, not native rendering or network execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'shared/src'
UI = SOURCE / 'commonMain/kotlin/dev/ohmydrift/ui/settings'


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
        actions = (UI / 'MirrorPageActions.kt').read_text()
        self.assertNotIn('DIRECT_MIRROR_KEY', actions)
        self.assertIn('if (mirrors.isNotEmpty()) MirrorProbeBar', page)

    def test_only_bottom_button_submits_and_selector_has_no_card(self):
        editor = (UI / 'MirrorEditor.kt').read_text()
        owner = (UI / 'MirrorPageState.kt').read_text()
        dismiss = owner.split('fun dismiss()', 1)[1].split('fun finishDismiss()', 1)[0]
        self.assertNotIn('submit(', dismiss)
        self.assertIn('text = when { readFailed -> "重试读取"; draft.saving > 0 -> "保存中…"; else -> "保存" }', editor)
        self.assertNotIn('onFocusChanged', editor)
        self.assertNotIn('onSave()', editor.split('internal fun MirrorEditorSave', 1)[0])
        selector = editor.split('// The selector is a plain row:', 1)[1].split('EditorLabel("地址"', 1)[0]
        for token in ('SettingsCard(', '.background(', '.border(', '.chromeMaterial('):
            self.assertNotIn(token, selector)
        self.assertIn('session.changeConfirmed', owner)
        self.assertIn('mirrorEnabled = current.mirrorEnabled && remaining.isNotEmpty()', owner)

    def test_ios_menu_suppression_is_input_scoped(self):
        ios = (SOURCE / 'iosMain/kotlin/dev/ohmydrift/ui/settings/SettingsInputMenu.ios.kt').read_text()
        android = (SOURCE / 'androidMain/kotlin/dev/ohmydrift/ui/settings/SettingsInputMenu.android.kt').read_text()
        self.assertIn('LocalTextToolbar provides HiddenSettingsTextToolbar', ios)
        self.assertIn('TextToolbarStatus.Hidden', ios)
        self.assertIn('= content()', android)
        self.assertEqual(2, (UI / 'MirrorEditor.kt').read_text().count('SettingsInputMenu {'))
        self.assertIn('SettingsInputMenu {', (UI / 'SettingsControls.kt').read_text())
        self.assertNotIn('LocalTextToolbar', (ROOT / 'shared/src/commonMain/kotlin/dev/ohmydrift/App.kt').read_text())


if __name__ == '__main__':
    unittest.main()
