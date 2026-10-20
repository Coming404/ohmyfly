# Melox / AndroidLiquidGlass / Miuix glass provenance

## Fixed source

This integration uses [Inefy-03/Melox](https://github.com/Inefy-03/Melox), commit
`49ee06aa1f01a67eb5ea1d5cc7307ddfd571a534` (2026-10-20 review baseline), specifically
`app/src/main/java/com/melox/player/ui/component/liquid/` and `animation/`.
No player, Android host, assets, or Melox navigation state was imported.

## Per-file notices

The following upstream files carry `Copyright 2026, compose-miuix-ui contributors`
and `SPDX-License-Identifier: Apache-2.0`:

| Upstream file | ohmydrift adaptation |
| --- | --- |
| `liquid/LiquidGlassNavigationBar.kt` | `ui/glass/GlassNavigationSurface.kt`, `GlassNavigationGesture.kt`, `GlassNavigationTabs.kt`; gravity/light preset in `GlassHighlight.kt` |
| `liquid/CombinedBackdrop.kt` | `ui/glass/CombinedBackdrop.kt` |
| `liquid/Lens.kt` | `ui/glass/Lens.kt` |
| `liquid/InnerShadow.kt` | `ui/glass/InnerShadow.kt` |
| `animation/InteractiveHighlight.kt` | `ui/glass/InteractiveHighlight.kt` |
| `animation/DampedDragAnimation.kt` | `ui/glass/DampedDragAnimation.kt` |

The navigation, Lens and animation source also attributes
[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), Apache-2.0.
These copyright and origin notices remain in the adapted files. `GlassHighlight.kt`
also adapts Miuix 0.9.4's Apache-2.0 BloomStroke shader to the shared cubic boundary.
The Apache-2.0 terms are included verbatim in
[licenses/glass-Apache-2.0.txt](licenses/glass-Apache-2.0.txt).

`liquid/GlassContainerBlur.kt` has no per-file license header. Its Melox 4dp / 0.45
sigma policy informs `ui/glass/GlassEffects.kt` and `GlassPreset.kt`; these files
conservatively record Melox's project-level GPL-3.0 provenance. The project-level GPL version 3 terms are
copied verbatim from the fixed local Melox checkout into
[licenses/Melox-GPL-3.0.txt](licenses/Melox-GPL-3.0.txt). The per-file Apache notices
do not relicense Melox as a whole, and the template's Apache license does not
supersede GPL obligations.

## Modifications

- Move the implementation into shared Compose commonMain; use Miuix's public
  Backdrop, runtime-shader and blur APIs rather than Android BlurEffect's
  radius-to-sigma conversion on iOS.
- Retain offscreen accent icon/label recording and CombinedBackdrop sampling;
  split the original 655-line navigation by drawing, tabs, gestures and lighting.
- Keep the app's existing five destinations, one committed destination state,
  24dp icons / 11sp always-visible labels, 4dp gap, font-growing minimum 64dp
  cells, safe-area and keyboard/accessibility behavior.
- Drive both bars from one four-mode preset table. Disable recording and sensor
  use for reduced transparency or unavailable runtime shaders; suppress liquid
  deformation and nonessential light motion when platform animation scale is zero.
- Replace the rounded-rectangle approximation with the same Miuix smooth cubic
  boundary in clipping, lens refraction and BloomStroke lighting; keep the
  original press, velocity deformation, dispersion, rubber-band and spring paths.
- Consume the separately owned y-up screen-gravity API; map it to shader y-down
  only when computing the light. No sensor implementation is copied here.

## Distribution boundary

This notice records provenance; it is not a project-wide relicensing decision or
release approval. Any distribution containing GPL-covered adaptations must satisfy
the applicable GPL requirements (including corresponding source and notices).
Check combined-work licensing and iOS distribution terms before a release; retaining
these files alone does not establish distribution compliance.
