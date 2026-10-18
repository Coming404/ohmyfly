# Agent Note: Expressive 动态主题与概览页原生 Miuix 组件排版

Status: implemented

## Problem

用户在 iOS 实看后认为展示页字号与留白偏大，要求 Expressive + Spec2025 + MonetSystem，并先修改一个页面验证；另明确加入 miuix-blur，将 Android 最低版本提升到 Android 13 / API 33，iOS 仍为 16.0。

## Decision

- 共享入口使用 ThemeController 的 MonetSystem、Vibrant、Spec2025（用户后续将调色板从 Expressive 改为 Vibrant）；既有蓝色 0xFF3482FF 暂保留为种子，使样式和规范在 Android/iOS 都进入 colorsFromSeed 路径。种子来源不必固定，但0.9.4在 keyColor 为空时转入 platformDynamicColors，不消费所传 paletteStyle/colorSpec；当前没有接入动态种子来源，不通过删除参数假装保持 Vibrant。跟随系统指明暗模式，而非读取 iOS 壁纸。
- 仅重排概览内容，采用官方 SmallTitle / BasicComponent / Card 默认字号、内距、圆角，卡片外侧与间距参考同 tag example 的12dp；不自造全局缩放，不改变另外两页布局或字体家族。
- 同版本 miuix-blur 0.9.4 用于概览顶栏。滚动层的 layerBackdrop 先绘制不透明表面色，再由透明顶栏的 textureBlur 采样；采用官方示例25f半径与0.8表面色覆盖。Scaffold 边距交给概览列表 contentPadding 而不是裁切 viewport，让内容实际滚入顶栏下方。减少动态效果开关同时停用模糊，其他页使用不透明顶栏。
- Android App 与共享库 minSdk 都由版本目录中的33驱动，不提升iOS16.0目标。Klibs与真实变体、Android AAR/源码门槛及iOS验证边界见[依赖记录](../../../../docs/research.md)。初次模糊接入依赖传递shader；后续用户要求[全部模块](2026-10-18-miuix-full-modules.md)，现已显式声明，不增加其他模糊库。
- 用户授权构建前删除全部旧 Actions runs，10条记录已删除并确认剩余0；Git历史与签名备份保留。新安装包只留GitHub，不下载。

## Related decisions

[统一悬浮与系统配色](../bug-fix/2026-10-19-floating-chrome.md)由用户新决定部分替代本篇：不再固定蓝种子/Vibrant，顶底五页统一材质；API33/iOS16下限与Miuix组件边界保留。本文保留先前指定风格与种子分支的取舍，不作为当前配色默认值。

[原 UI 展示决定](2026-10-18-ui-showcase.md)部分被本篇取代：三页、演示数据、状态、无后端与选择语义仍保留，静态色板和不引入模糊的约束由新需求替换。架构笔记与基础提案描述初建阶段，不属于本次全部重写范围。当前需求文档、工程规范、依赖记录和最低版本检查同步。

[五入口悬浮导航](2026-10-18-five-destinations.md)在本篇基础上扩展概览快捷入口并将外观页收敛为设置页；主题与概览模糊边界继续保留。

## Alternatives considered

- 保留无种子的 MonetSystem：能读取Android系统色，但无法保证Expressive/Spec2025生效，iOS回退也不符合指定风格，因此不采用。
- 只缩小现有大标题和内距：差异最小，但仍是自定义品牌展示排版，不符合用Miuix默认组件作对照的目的。
- 全部三页同时重排：视觉一致性更强，但不利于用户单页比较，超出本次范围。

## Consequences

- 默认列表行替代32sp大标题与大号统计卡，页面密度由官方组件默认值支撑，而非随意整体缩放；官方example是参考惯例，不宣称强制模板。
- Android12不再支持；iOS目标保持16.0。Miuix仍experimental，模糊的最低系统性能、字体与安全区需要真机实看。
- 本地运行结构检查及Python回归测试（包括最低版本回退拒绝），不下载Gradle/SDK；完整双端Release与commonTest交给Actions，具体结果以新run为准，不把静态通过当作编译或真机验收。
