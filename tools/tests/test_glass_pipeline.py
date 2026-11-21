"""Glass source integration guards; do not validate pixels or compile shader/Kotlin."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'
GLASS = COMMON / 'ui/glass'


class GlassPipelineTests(unittest.TestCase):
    def test_material_modules_share_one_backend_and_keep_geometry_separate(self):
        import tomllib
        catalog = tomllib.loads((ROOT / 'gradle/libs.versions.toml').read_text())
        modules = {entry.get('module') for entry in catalog['libraries'].values()}
        self.assertNotIn('io.github.kyant0:backdrop', modules)
        self.assertNotIn('top.yukonga.miuix.kmp:miuix-shader', modules)
        self.assertIn('io.github.kyant0:shapes', modules)
        self.assertIn('top.yukonga.miuix.kmp:miuix-glass', modules)
        self.assertIn('top.yukonga.miuix.kmp:miuix-glass-icons', modules)
        gate = (GLASS / 'GlassAppearance.kt').read_text()
        self.assertIn('!reduceTransparency && !systemReduceTransparency && shaderSupported', gate)
        self.assertNotIn('com.kyant.backdrop', gate)

    def test_g2_patch_keeps_precompiled_file_facades(self):
        root = ROOT / 'ui-shapes/src/commonMain/kotlin/top/yukonga/miuix/kmp/squircle'
        for name in ('SquirclePath', 'SquircleBorder', 'SquircleBackground', 'LocalSquircleEnabled'):
            self.assertFalse((root / (name + '.kt')).exists())
            self.assertIn(f'@file:kotlin.jvm.JvmName("{name}Kt")', (root / ('Diy_' + name + '.kt')).read_text())
        self.assertTrue((ROOT / 'third-party/licenses/glass-Apache-2.0.txt').is_file())


if __name__ == '__main__':
    unittest.main()
