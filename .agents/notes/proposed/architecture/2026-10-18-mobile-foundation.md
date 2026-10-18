# Agent Note: ohmydrift 双端技术基础

Status: proposed

## Problem

iOS16是要求。Kotlin共享UI、Miuix页面规范和Go本地核心需要清晰的工程边界，不能把结构创建误认为跨平台能力已验证。

## Proposal

采用 Android/iOS 共用 Kotlin UI与业务、平台宿主适配、Go内嵌核心。页面使用 Miuix Scaffold；工程底座优先裁剪官方 Kotlin/KMP-App-Template。iOS16是要求，Android12/API31暂定。产品名已确定为 ohmydrift。

版本策略：Miuix正式发布0.9.4为候选，API仍实验性。工具链先参考该tag的版本目录，在Actions验证后锁定。不把Klibs latestStableVersion=null描述成已认证稳定。

完整构建测试在GitHub Actions执行，本地保留已安装JDK17，不安装全局Gradle/SDK/NDK，也不为最新工具链升级本地JDK。工作流仍需选择兼容且固定的云端版本。

Go抽取策略：参考subs-check-pro订阅解析，修复去重凭证及参数丢失问题，复用clash-speedtest测速思路；不完整嵌入桌面/Web服务程序。不把处理结果重新送回旧server:port去重。

## Alternatives considered

- Android Kotlin+iOS Swift两套原生UI：系统交互直接，但重复维护不符合本轮共享UI目标。
- 直接使用Miuix example：组件最齐全，但平台/构建逻辑偏重，iOS示例目标并非16，适合参考而非原样复用。
- 整包嵌入subs-check-pro：功能多但依赖复杂；更适合提取独立能力，并维护来源、补丁和GPL义务。

## Acceptance criteria

- 形成需求、工程/视觉、证据与资料入口文档，明确需求与建议、未验收项。
- 双端空工程先验证Miuix Scaffold，再验证Go桥接最小任务、取消和释放。
- iOS16/API31有依赖和运行证据，不为省事静默提高最低版本。
- 修复过的去重用边界测试保护，吞吐测试不因无限节点并发竞争出口而失真。
- 所有云端签名、秘密、GPL与分发约束在发布前独立检查。

## Risks

Miuix仍实验性；iOS16及Go全依赖树未编译验证；Android12不能直接依赖minSdk33的miuix-blur；上游tag文档与主分支可能不同；项目模板的预装Xcode路径可能失效。App Store/GPL合规尚未定案。名称未经查重。

## Related documents

- [需求](../../../../docs/requirements.md)
- [工程](../../../../docs/engineering.md)
- [选型证据](../../../../docs/research.md)

更完整的Go核心与测速方案仍是提案；最小工程结构的落地情况见[工程底座记录](../../implemented/architecture/2026-10-18-project-foundation.md)。
