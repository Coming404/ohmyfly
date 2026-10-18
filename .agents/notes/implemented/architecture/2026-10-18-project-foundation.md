# Agent Note: 双端最小工程、规则与资料入口

Status: implemented

## Problem

开发需要可追踪的双端工程、统一资料查询入口及轻量校验，不能只保留聊天规划，也不能把完整构建负担放在手机环境。

## Decision

工程保留官方KMP模板的Wrapper和iOS宿主，新增Android宿主、共享MiuixTheme/Scaffold空页。只启用Android与iOS；iOS deployment target设16.0，Android API31暂定。基础API与版本对齐Miuix0.9.4标签，构建声明已写入Version Catalog与Wrapper，但尚无云端构建通过证据。

`AGENTS.md`承载工作规则：选KMP库先查询现有Klibs MCP，缺失的许可证/维护信号再查官方来源；查询入口在`docs/references.md`集中维护。Klibs当前工具没有全部用户关心字段，禁止凭空推断许可证或维护保证。

已复制的决策记录原则调整为项目内可执行流程：去除机器绝对路径和npm命令依赖，使用Python检查文件关系、声明、文档和笔记格式，并给检查器增加边界测试。

基础工作流分轻量检查和手动云端编译。云端编译没有自动发布或签名；iOS只准备无签名模拟器产物。native-core仅预留，不创建虚假可用后端。

## Source and evidence

- 官方模板：Kotlin/KMP-App-Template默认分支归档，下载于2026-10-18。
- 下载归档SHA256：`adf3ee2a5fcf8fa297c4ba1e0b3ddf477850ded1dc3c3a9469f262d5f5ff7f43`。此前的tree SHA不冒充本次下载commit。
- Wrapper JAR SHA256：`497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`；来源指纹并非Gradle签名认证。
- Klibs查询：miuix-ui最新0.9.4，stable字段null；searchProjects确认Android、ios_arm64、ios_simulator_arm64，作者compose-miuix-ui，排除同名fork。
- Compose foundation最新1.12.2，但保持对齐Miuix的1.12.0声明。API/许可核对入口见[资料索引](../../../../docs/references.md)。
- 原始去重边界测试输出保存在[证据](../../../../docs/evidence/dedup-results.txt)，避免清理调研副本时丢失结论。

## Alternatives considered

- 原样复制Miuix example：组件用法完整，但同时包含库源码与多平台构建配置，不是独立精简Starter；只作API参考。
- 假设存在官方Miuix Starter：组织仓库与文档本次未发现，不能虚构入口；以后有新模板再评估。
- 安装本地完整工具链：能更快发现部分编译问题，但与设备空间和云端构建流程不符，当前只做轻量验证。
- 增加完整分层框架/DI/导航/持久化：最初需求不足而不引入；用户后续明确要求的非敏感设置存储由[设置与镜像](../feature/2026-10-19-settings-mirrors.md)单独接入，不扩大到完整DI／数据库体系。

## Consequences

- 已创建的是可交接的最小工程结构，不是编译、签名或真机验收完成。
- iOS16/API31、AGP DSL、固定Xcode/runner可用性必须经首轮Actions验证，不能靠静态检查证明。
- 下一步按[开发顺序](../../../../docs/development.md)运行云端空工程，再验证Go桥接。
- 应用标识和模板图标仅供开发，发布前确定品牌、签名、许可和源码提供方案。
- [共享 UI 展示扩展](../feature/2026-10-18-ui-showcase.md) 在此底座上实现三栏导航与演示控件，替代居中占位页；不改变本文的平台、Go 或构建验证边界。
- 更完整的Go与测速架构仍在[提案](../../proposed/architecture/2026-10-18-mobile-foundation.md)，未将未来能力移成已实现。
