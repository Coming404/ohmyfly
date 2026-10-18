# Agent Note: Miuix 全模块与完整图标

Status: implemented

## Problem

用户明确要求引入锁定版 Miuix 的全部正式模块，并完整引用图标模块，而不是仅使用基础图标子集。现有项目只有 ui/blur 的直接声明和少量自绘通用图标。

## Decision

- 在 Version Catalog 和 shared/commonMain 中显式声明0.9.4的全部8个发布模块：miuix-ui、miuix-core、miuix-preference、miuix-icons、miuix-blur、miuix-shader、miuix-squircle、miuix-nav。统一引用已有 miuix 版本键，不包含example/docs/baselineprofile。
- 使用完整 miuix-icons，保留 miuix-ui 提供的Basic；不引入不存在的icons-basic。通用导航/操作改用上游图标，移除对应自绘路径，仅品牌Wave仍为原创标识。
- 五个导航图标采用Home、Link、Stopwatch、Layers、Settings；基础动作采用Basic.ArrowRight、Basic.Close、Basic.Check。扩展图标全部权重由原artifact提供，不裁剪源码或复制单独SVG。
- 引入miuix-nav不等于立即改写路由；五个顶层目的地继续复用现有会话状态。未授权完整导航架构迁移，不为使用模块而增加复杂度。

## Evidence

- 子代理先查klibs MCP，再核对v0.9.4固定tag、Maven POM/module及Android/iOS变体。8个模块均返回0.9.4，latestStableVersion均null，不代表API稳定；模块目标包含Android、ios_arm64、ios_simulator_arm64。具体兼容边界见[依赖记录](../../../../docs/research.md)。
- 官方[模块清单](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/settings.gradle.kts)、[发布说明](https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4)、[图标文档](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/docs/zh_CN/guide/icons.md)。0.9.4用miuix-nav替代已删除的miuix-navigation3-ui，不混用旧包。

## Related decisions

检索现有笔记：[三页展示](../../implemented/feature/2026-10-18-ui-showcase.md)中的不引入完整图标/preference仅适用于早期演示，被本篇部分替代，其他演示和选择边界保留；[概览主题](../../implemented/feature/2026-10-18-expressive-overview.md)中不显式声明shader由本篇替代，Vibrant和种子处理不变；[五页导航](../../implemented/feature/2026-10-18-five-destinations.md)保留，不借依赖接入迁移路由。

## Alternatives considered

- 只显式引入ui/preference/icons/blur/nav，其余靠api传递：声明更少且运行图可覆盖8模块，但本次用户强调全部模块，显式列全更容易审计，统一version.ref避免重复手写版本。
- 保留自绘通用图标：无额外图标依赖，但不符合完整图标模块要求；只保留有品牌意义的原创Wave，不并行保留同类通用路径。
- 升级或混用旧navigation3-ui：旧示例更常见，但0.9.4已删除旧模块，不跨版本拼装。

## Consequences

- 8个坐标在catalog和commonMain中均显式引入并共享0.9.4，图标引用完整artifact；不存在旧nav包或臆造分组，原通用自绘路径已删除，品牌Wave保留。
- 引入依赖不等于业务已使用全部组件，nav没有擅自替代现有五页状态路由；图标仍按需import，完整库可用，不逐个复制源码。
- 本地执行结构检查、19个Python测试与差异检查，并额外断言8模块统一版本和Gradle声明完整；这些不是依赖解析或Kotlin编译。
- 库仍experimental；新增模块尚未构建，iOS16链接/运行待验。ui传递material3-window-size-class:1.12.0-alpha03，不宣称整个依赖树无预发布。完整库引入不代表安装包保留每个未用图标，不为此关闭优化器。
