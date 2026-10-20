# Agent Note: Android 动态来源与 Neutral 2025 配色

Status: implemented

## Problem

用户要求设置重设计逐项交付、每项完成后等待确认。首项保留 Android 壁纸动态取色，同时保留 Neutral＋Spec2025；iOS 暂不改。Miuix 0.9.4 的 keyColor=null 直接调用 platformDynamicColors，不消费 paletteStyle/colorSpec，不能只追加两个参数声称达标。

## Decision

- Android 宿主读取公开系统资源`android.R.color.system_accent1_500`作为非空动态代表色。它来自系统当前配色，不保证等于壁纸原始种子；系统选择固定基础色时也跟随用户选择。
- `App`接收实际`dynamicColorSeed: Color?`，替代旧能力布尔值。共享`appThemeController`在非空种子时使用MonetSystem、Neutral、Spec2025；remember同时依赖种子和已有明暗配置。
- iOS传空种子，继续使用System和内置浅深色，不读取iOS壁纸。仅参数接线变化，配色不变。
- 启动在setContent前读取系统色，onResume重新读取。Manifest没有接管configChanges，资源配置变更由系统默认重建Activity处理。不增加壁纸、媒体或存储权限。
- 种子不持久化、不固定为蓝色；原有设置、明暗选择和网络行为不改，不新增全局Store或依赖。
- [第7项短过渡](2026-10-20-reduced-motion.md)只增加显示颜色插值，保留官方controller根和动态模式标记；colors覆盖继承该标记，不改变本篇取色与生成策略。

## Existing decisions audit

- [统一悬浮材质](../../implemented/bug-fix/2026-10-19-floating-chrome.md)：部分取代仅Android的平台整套动态色分支；共享布局、模糊参数及iOS取色暂保留。
- [早期概览主题](../../implemented/feature/2026-10-18-expressive-overview.md)：保留非空种子才应用style/spec的源码事实，不恢复旧固定蓝色/Vibrant。
- [设置持久化](../../implemented/feature/2026-10-19-settings-mirrors.md)：复用明暗配置，无存储迁移。

## Alternatives considered

- 继续keyColor=null：最少改动且跟随平台，但无法同时保证Neutral/Spec2025。
- 固定#3482FF：双端可复现，但用户最新明确改回动态来源。
- 直接读取壁纸图片：更接近原始图片取色，但不尊重用户选择的系统基础色，也会扩大权限和适配边界。

## Consequences

- Android由“沿用整套平台动态色”改为“系统动态代表色→Neutral/Spec2025”，不会得到与系统其他App完全相同的调色板。
- 新增3项Kotlin配置测试：非空种子与明暗、iOS内置配色、变更种子不被固定品牌色覆盖。测试尚未运行，完整编译和真机更新行为仍待后续授权验证。
- 新增Python接线/权限守卫；27项Python、结构与差异检查通过。静态守卫不证明Kotlin编译或动态色视觉效果。
- [设计方案](../../../../docs/settings-redesign.md)记录改造前/后值和逐项进度。本项交付后停下等用户确认，不自动提交、推送或触发构建。
