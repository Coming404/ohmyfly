"""Static guards for settings-row feedback scope and gesture ownership."""
import unittest
from pathlib import Path

SETTINGS = Path(__file__).resolve().parents[2] / 'shared/src/commonMain/kotlin/com/ohmyfly/ui/settings'


class SettingsPressFeedbackTests(unittest.TestCase):
    def test_only_interactive_rows_override_the_indication(self):
        for name in ('SettingsActionRow.kt', 'ChoiceSetting.kt', 'SettingsExpansion.kt'):
            with self.subTest(name=name):
                source = (SETTINGS / name).read_text()
                self.assertEqual(1, source.count('SettingsRowFeedback {'))
                self.assertEqual(1, source.count('BasicComponent('))
        controls = (SETTINGS / 'SettingsControls.kt').read_text()
        toggle = controls.split('internal fun ToggleSetting(', 1)[1].split('@Composable', 1)[0]
        self.assertEqual(1, toggle.count('SettingsRowFeedback {'))
        self.assertEqual(1, controls.count('SettingsRowFeedback {'))
        self.assertNotIn('SettingsRowFeedback', (SETTINGS / 'SettingsComponents.kt').read_text())

    def test_click_and_haptic_ownership_stays_with_the_existing_row(self):
        for name in ('SettingsActionRow.kt', 'SettingsExpansion.kt'):
            with self.subTest(name=name):
                source = (SETTINGS / name).read_text()
                self.assertEqual(1, source.count('onClick = hapticClick(onClick)'))
                self.assertNotRegex(source, r'\.(?:clickable|pointerInput)\(')
        choice = (SETTINGS / 'ChoiceSetting.kt').read_text()
        self.assertEqual(1, choice.count('onClick = hapticClick {'))
        self.assertNotRegex(choice, r'\.(?:clickable|pointerInput)\(')
        controls = (SETTINGS / 'SettingsControls.kt').read_text()
        toggle = controls.split('internal fun ToggleSetting(', 1)[1].split('@Composable', 1)[0]
        self.assertEqual(1, toggle.count('.toggleable('))
        self.assertIn('enabled = enabled, role = Role.Switch, onValueChange = hapticChange(onChange)', toggle)
        self.assertIn('onCheckedChange = null', toggle)
        self.assertIn('Modifier.clearAndSetSemantics {}', toggle)

    def test_popup_keeps_its_own_indication_and_hold_lifetime(self):
        choice = (SETTINGS / 'ChoiceSetting.kt').read_text()
        self.assertLess(choice.index('val popupIndication = LocalIndication.current'),
                        choice.index('SettingsRowFeedback {'))
        self.assertRegex(choice, r'CompositionLocalProvider\(LocalIndication provides popupIndication\)\s*\{\s*OverlayDropdownPopup\(')
        self.assertIn('holdDownState = held', choice)
        self.assertIn('onDismiss = { expanded = false;', choice)
        self.assertIn('onDismissFinished = { held = false;', choice)
        self.assertIn('enabled = options.isNotEmpty()', choice)

    def test_feedback_cannot_consume_gestures_or_wait_for_press_animation(self):
        feedback = (SETTINGS / 'SettingsRowFeedback.kt').read_text()
        self.assertIn('IndicationNodeFactory', feedback)
        self.assertIn('DrawModifierNode', feedback)
        self.assertIn('LocalIndication provides indication', feedback)
        self.assertNotRegex(feedback, r'\b(?:delay|join|cancelAndJoin|awaitPointerEvent|consume|onClick|hapticClick|hapticChange)\s*\(')
        self.assertNotRegex(feedback, r'\.(?:clickable|toggleable|pointerInput)\(')
        for interaction in ('PressInteraction.Cancel', 'FocusInteraction.Focus',
                            'HoverInteraction.Enter', 'HoldDownInteraction.HoldDown',
                            'HoldDownInteraction.Release'):
            with self.subTest(interaction=interaction):
                self.assertIn(interaction, feedback)


if __name__ == '__main__':
    unittest.main()
