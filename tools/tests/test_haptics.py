"""Shared haptic wiring guards only; Kotlin execution and native feedback need separate checks."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'


class HapticWiringTests(unittest.TestCase):
    def test_defaults_and_three_levels_are_shared_settings(self):
        model = (COMMON / 'settings/AppSettings.kt').read_text()
        levels = (COMMON / 'settings/HapticLevel.kt').read_text()
        self.assertRegex(model, r'\bhapticEnabled\s*:\s*Boolean\s*=\s*true\b')
        self.assertRegex(model, r'\bhapticLevel\s*:\s*HapticLevel\s*=\s*HapticLevel\s*\.\s*Medium\b')
        self.assertRegex(levels, r'\benum\s+class\s+HapticLevel\b')
        for level in ('Light', 'Medium', 'Heavy'):
            with self.subTest(level=level):
                self.assertRegex(levels, rf'(?m)^\s*{level}\b')

    def test_codec_keeps_typed_optional_keys_and_schema_one(self):
        codec = (COMMON / 'settings/SettingsCodec.kt').read_text()
        self.assertRegex(codec, r'\bCURRENT_SCHEMA_VERSION\s*=\s*1\b')
        for field, key_type, stored_value, reader in (
            ('hapticEnabled', 'boolean', r'hapticEnabled\b',
             r'read\s*\(\s*hapticEnabled\s*,\s*defaults\s*\.\s*hapticEnabled\s*\)'),
            ('hapticLevel', 'string', r'hapticLevel\s*\.\s*name\b',
             r'readOrNull\s*\(\s*hapticLevel\s*\)\s*\.\s*toEnumOr\s*'
             r'\(\s*defaults\s*\.\s*hapticLevel\s*\)'),
        ):
            with self.subTest(field=field):
                self.assertRegex(codec, rf'\b{field}\s*=\s*{key_type}PreferencesKey\s*\(\s*"ui\.{field}"\s*\)')
                self.assertRegex(codec, rf'\[\s*{field}\s*\]\s*=\s*settings\s*\.\s*{stored_value}')
                self.assertRegex(codec, rf'\b{field}\s*=\s*preferences\s*\.\s*{reader}')

    def test_click_resolves_current_level_and_preview_is_gated(self):
        source = (COMMON / 'ui/AppHaptics.kt').read_text()
        self.assertRegex(source, r'\bclass\s+AppHapticFeedback\b')
        self.assertRegex(source, r'\bpreview\s*\(\s*level\s*\(\s*\)\s*\)')
        self.assertRegex(source, r'\bif\s*\(\s*enabled\s*\(\s*\)\s*\)\s*(?:\{\s*)?perform\s*\(\s*level\s*\)')

    def test_provider_uses_live_state_and_shared_platform_hook(self):
        source = (COMMON / 'ui/AppHaptics.kt').read_text()
        for field in ('enabled', 'level'):
            with self.subTest(field=field):
                self.assertRegex(source, rf'\brememberUpdatedState\s*\(\s*{field}\s*\)')
        self.assertRegex(source, r'\bexpect\s+fun\s+rememberPlatformHaptics\s*\(')
        self.assertRegex(source, r'\bLocalAppHaptics\s+provides\s+feedback\b')
        self.assertRegex(source, r'\bLocalHapticFeedback\s+provides\s+componentFeedback\b')
        self.assertRegex(source, r'\bcurrentEnabled\s*\.\s*value\b')
        self.assertRegex(source, r'\bcurrentLevel\s*\.\s*value\b')


if __name__ == '__main__':
    unittest.main()
