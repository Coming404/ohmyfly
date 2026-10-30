"""Glass source integration guards; do not validate pixels or compile shader/Kotlin."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / 'shared/src/commonMain/kotlin/com/ohmyfly'
GLASS = COMMON / 'ui/glass'


class GlassPipelineTests(unittest.TestCase):
    def test_each_material_uses_one_parameterized_effect_chain(self):
        presets = (GLASS / 'GlassPreset.kt').read_text()
        effects = (GLASS / 'GlassEffects.kt').read_text()
        for mode in ('GaussianBlur', 'HighlightGlass', 'SoftGlass', 'LiquidGlass'):
            self.assertIn(f'MaterialMode.{mode} -> GlassPreset(', presets)
        self.assertIn('liquidResponse = true', presets)
        self.assertIn('!reduceTransparency && !systemReduceTransparency && shaderSupported', presets)
        self.assertIn('MotionDurationScale', (COMMON / 'ui/AppMotion.kt').read_text())
        self.assertNotIn('rememberGlassMotionAllowed', presets)
        catalog = (ROOT / 'gradle/libs.versions.toml').read_text()
        self.assertIn('androidx-lifecycle-runtime-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }', catalog)
        self.assertIn('implementation(libs.androidx.lifecycle.runtime.compose)', (ROOT / 'shared/build.gradle.kts').read_text())
        ios = (ROOT / 'shared/src/iosMain/kotlin/com/ohmyfly/ui/glass/sensor/GlassGravity.ios.kt').read_text()
        for constant in ('UIInterfaceOrientationPortrait', 'UIInterfaceOrientationLandscapeRight',
                         'UIInterfaceOrientationPortraitUpsideDown', 'UIInterfaceOrientationLandscapeLeft',
                         'UISceneActivationStateForegroundActive'):
            self.assertIn(f'import platform.UIKit.{constant}\n', ios)
        self.assertNotIn('UIInterfaceOrientation.UIInterfaceOrientation', ios)
        self.assertNotIn('UISceneActivationState.UISceneActivationState', ios)
        for operation in ('colorControls(', 'blur(preset.blur.dp.toPx())', 'lens('):
            self.assertIn(operation, effects)
        self.assertEqual(1, effects.count('effects = { glassEffects('))
        self.assertIn('onDrawSurface = { drawRect(surface.copy(alpha = preset.surfaceAlpha)) }', effects)
        self.assertNotIn('textureBlur(', effects)

    def test_press_does_not_add_a_uniform_white_plus_layer(self):
        highlight = (GLASS / 'InteractiveHighlight.kt').read_text()
        self.assertNotRegex(
            highlight,
            r'drawRect\(\s*color\s*=\s*Color\.White(?:\.copy\([^)]*\))?,'
            r'\s*blendMode\s*=\s*BlendMode\.Plus',
        )

    def test_refraction_displaces_input_sampling_and_shares_boundary(self):
        lens = (GLASS / 'Lens.kt').read_text()
        self.assertIn('runtimeShaderEffect(', lens)
        self.assertIn('uniformShaderName = "content"', lens)
        self.assertIn('shape as? GlassShape', lens)
        self.assertIn('$GLASS_BOUNDARY_SDF', lens)
        self.assertIn('float2 refractedCoord = coord + d * grad', lens)
        self.assertIn('content.eval(refractedCoord)', lens)
        self.assertIn('chromaticAberration', lens)
        self.assertNotIn('android.graphics', lens)
        self.assertNotIn('CornerBasedShape', lens)

    def test_upstream_attribution_and_combined_layers_are_retained(self):
        combined = (GLASS / 'CombinedBackdrop.kt').read_text()
        self.assertIn('Backdrop', combined)
        self.assertIn('SPDX-License-Identifier: Apache-2.0', combined)
        self.assertIn('SPDX-License-Identifier: Apache-2.0', (GLASS / 'Lens.kt').read_text())
        self.assertIn('Kyant0/AndroidLiquidGlass', (GLASS / 'Lens.kt').read_text())
        self.assertTrue((ROOT / 'third-party/licenses/Melox-GPL-3.0.txt').is_file())
        self.assertTrue((ROOT / 'third-party/licenses/glass-Apache-2.0.txt').is_file())
        self.assertIn('49ee06a', combined + (GLASS / 'GlassEffects.kt').read_text())
        for file in GLASS.glob('*.kt'):
            if file.name == 'GlassPresetAnimation.kt':
                continue  # Original application policy, not an upstream-derived rendering file.
            header = file.read_text()[:800]
            self.assertTrue('SPDX-License-Identifier' in header or 'Derived from' in header, file.name)


if __name__ == '__main__':
    unittest.main()
