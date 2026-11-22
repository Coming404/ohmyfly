# Third-party provenance

The Gradle Wrapper, iOS host project, editor configuration and starter layout are adapted from [Kotlin/KMP-App-Template](https://github.com/Kotlin/KMP-App-Template), Apache-2.0. A copy of the upstream license is in [licenses/kmp-app-template-Apache-2.0.txt](licenses/kmp-app-template-Apache-2.0.txt).

The template archive was downloaded from the default branch on 2026-10-18. Its exact SHA-256 is recorded in the foundation decision. A previously inspected tree SHA is not used as the download commit unless independently verified.

Changes: remove demo application/network/DI/navigation dependencies; retain Android/iOS only; use Miuix 0.9.5-beta03; configure ohmydrift development identifiers and iOS16; replace sample app bootstrap; pin build declarations and add project checks.

The copied license covers the template contribution, not all future project code. Go/Mihomo/upstream parsing code has not been imported. Its license obligations must be evaluated and recorded before distribution. Do not assume the template's Apache license overrides GPL obligations.

The iOS app icon currently comes from the template and is a development placeholder, not a final product asset.

## sublinkPro subscription usage

`shared/src/commonMain/kotlin/com/ohmyfly/subscription/SubscriptionUsage.kt` adapts `ParseSubscriptionUserInfo` from `sub.go` in [ZeroDeng01/sublinkPro](https://github.com/ZeroDeng01/sublinkPro/tree/34e93e695e73ea34f3a1e14a742e41ff10891a66), commit `34e93e695e73ea34f3a1e14a742e41ff10891a66` (2026-11-04), under MIT, Copyright (c) 2025 ZeroDeng. The full license is retained in [licenses/sublinkPro-MIT.txt](licenses/sublinkPro-MIT.txt).

The Kotlin port preserves valid zero values and uses nullable non-negative `Long` fields instead of zero defaults or a negative failure sentinel. Usage comes from the existing subscription GET response, follows the selected root snapshot, and stays in memory. The standalone HEAD/GET query from `usage.go` is not included. Integration behavior and differences are documented in [subscription references](../docs/references.md#订阅用量与到期信息).

## sublinkPro content hash and deduplication

`native-core/internal/flight/content_*.go` and `dedup*.go` adapt `node/protocol/protocol_hash.go`, the `Proxy` model in `node/protocol/clash.go`, airport advanced rules/name-set classification in `node/sub.go`, and output rules in `models/subcription.go` from the same fixed sublinkPro commit `34e93e695e73ea34f3a1e14a742e41ff10891a66` (MIT, Copyright (c) 2025 ZeroDeng). The retained [MIT license](licenses/sublinkPro-MIT.txt) covers these portions.

The port maps normalized Mihomo fields to upstream Go field names and scalar types, retains unknown configuration as semantic data instead of YAML AST positions, supports collection-valued rule fields, and uses JSON tuples instead of ambiguous delimiter keys. It does not copy credential-bearing normalization logs, server dependencies, or the unconditional EffectiveName output filter. SQLite performs batch and global matching before inserting formal nodes; duplicate nodes reuse an existing record while all accepted sources remain independently associated. Details are in the [native-core API](../native-core/README.md#三层去重与正式入库).

## sublinkPro 国家名称别名

`native-core/internal/catalog/region_aliases.go` 的城市、别称及非标准缩写改编自 [ZeroDeng01/sublinkPro 的默认国家规则](https://github.com/ZeroDeng01/sublinkPro/blob/a4565b05f7b37f363575bea5ea6ba745e1f0789f/models/db_migrate.go#L1233)，固定提交 `a4565b05f7b37f363575bea5ea6ba745e1f0789f`，MIT，Copyright (c) 2025 ZeroDeng。完整许可保留于 [licenses/sublinkPro-MIT.txt](licenses/sublinkPro-MIT.txt)。

适配时保留 OhMyFly 的国家目录，移除重复的标准名称、代码和国旗表达式，按现有最长名称与字母边界规则匹配。`LA` 保持老挝代码含义；展示继续使用 kmp-flagkit 图标。地区同步及旧节点重识别接入本项目 SQLite 升级事务。

## 鸣谢：连续曲率

- [Kyant Shapes 1.2.1](https://github.com/Kyant0/Shapes/tree/1.2.1)，Apache-2.0：`ui-shapes` 直接依赖的 G2 几何实现。
- [Miuix 0.9.5-beta03](https://github.com/compose-miuix-ui/miuix/tree/3526cfe2ca0779c5a2eab99af8737a96c9a8a885)，Copyright 2026, compose-miuix-ui contributors，Apache-2.0：形状适配保留公开 ABI，描边沿用内缩约定，改由矢量连续曲线统一填充和裁剪。许可见 [glass-Apache-2.0](licenses/glass-Apache-2.0.txt)，接口说明见 [ui-shapes](../ui-shapes/README.md)。

## 鸣谢：Miuix 玻璃材质

- [Miuix v0.9.5-beta03](https://github.com/compose-miuix-ui/miuix/tree/3526cfe2ca0779c5a2eab99af8737a96c9a8a885)，Copyright 2026, compose-miuix-ui contributors，Apache-2.0：玻璃组件、材质、Glass 图标与双端 shader。固定官方归档见 [miuix-material](miuix-material/README.md)，四个 `Diy_` 补丁与变更原因见 [材质适配](../ui-material/README.md)。
- Kyant Backdrop 运行库与全屏玻璃渲染已移除；历史导航的 capsule、inner lens 和按压手势按固定源码重新接入并适配 Miuix beta03/G2。Melox/Kyant 来源与文件级许可继续见 [Melox-NOTICE](Melox-NOTICE.md)。

## 鸣谢：Glasmio Clear 材质参数

- [NEORUAA/Glasmio](https://github.com/NEORUAA/Glasmio/tree/64b2d00746480c2136a77840cdb849c5505f8b6c/docs/native-materials.md)，固定提交 `64b2d00746480c2136a77840cdb849c5505f8b6c`，Apache-2.0：Clear 的0.75模糊、1.06饱和度、black/white 0.125/1.08、face matrix与maximum luma参数用作当前材质映射依据。项目保留 Miuix 双端 G2 Backdrop实现，不引入 Glasmio Android模块或其渲染器。
