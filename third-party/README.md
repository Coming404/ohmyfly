# Third-party provenance

The Gradle Wrapper, iOS host project, editor configuration and starter layout are adapted from [Kotlin/KMP-App-Template](https://github.com/Kotlin/KMP-App-Template), Apache-2.0. A copy of the upstream license is in [licenses/kmp-app-template-Apache-2.0.txt](licenses/kmp-app-template-Apache-2.0.txt).

The template archive was downloaded from the default branch on 2026-10-18. Its exact SHA-256 is recorded in the foundation decision. A previously inspected tree SHA is not used as the download commit unless independently verified.

Changes: remove demo application/network/DI/navigation dependencies; retain Android/iOS only; use Miuix 0.9.4; configure ohmydrift development identifiers and iOS16; replace sample app bootstrap; pin build declarations and add project checks.

The copied license covers the template contribution, not all future project code. Go/Mihomo/upstream parsing code has not been imported. Its license obligations must be evaluated and recorded before distribution. Do not assume the template's Apache license overrides GPL obligations.

The iOS app icon currently comes from the template and is a development placeholder, not a final product asset.

## Melox liquid-glass reference

`shared/src/commonMain/kotlin/dev/ohmydrift/ui/glass/` adapts the glass navigation from [Inefy-03/Melox](https://github.com/Inefy-03/Melox), commit `49ee06aa1f01a67eb5ea1d5cc7307ddfd571a534`. Files carrying a per-file `Copyright 2026, compose-miuix-ui contributors / SPDX-License-Identifier: Apache-2.0` header and a Kyant0/AndroidLiquidGlass attribution are used under Apache-2.0; their notice is in [licenses/glass-Apache-2.0.txt](licenses/glass-Apache-2.0.txt). Files derived from Melox code without such a header (the container blur/preset policy) are treated conservatively as GPL-3.0; the Melox tree license is in [licenses/Melox-GPL-3.0.txt](licenses/Melox-GPL-3.0.txt), and the per-file mapping is in [Melox-NOTICE.md](Melox-NOTICE.md).

Only the liquid-glass sampling, refraction, highlight and navigation interaction are adapted. Melox's media player, Android host, routing, typography and settings are not copied. This reference does not relicense the rest of ohmydrift or assert that distribution obligations are settled.
