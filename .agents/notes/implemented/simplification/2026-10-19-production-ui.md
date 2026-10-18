# Agent Note: 移除运行时展示数据与占位界面

Status: implemented

## Problem

用户明确要求按生产App开发，删除演示、预览、示例延迟、开发提示与相关生产文件命名。用户另确认：真实自动化测试保留标准Test命名及测试目录；不删除测试。

## Decision

- 删除commonMain里的4条固定节点及固定延迟，NodeSummary仅保留UI所需的真实字段，延迟可为空。测试用节点仅在commonTest中构造。
- 生产入口初始化为空目录与空选择，节点、概览、测速和批量操作接收同一节点列表，不再从全局展示数组读取。
- 删除概览展开体验、主题参数展品、提示页脚和设置里的开发能力说明，使用正式功能标题与正常空状态。订阅导入/网络测速未接入的事实仍写在工程文档中，不能用删除文字冒充实现。
- DemoNode/DemoComponents改为NodeSummary/NodeSelection/ChoiceOption；SpeedTestScreen改为SpeedScreen，真实自动化测试按职责保留Test后缀，DemoSelectionTest改为NodeSelectionTest。
- 清除不再使用的页面Crossfade、卡片展开状态及直接animation依赖；减少动态效果选项改为准确的减少透明效果，仅控制双栏材质回退，不承诺关闭Miuix内部全部动画。

## Related decisions

与[五入口导航](../../implemented/feature/2026-10-18-five-destinations.md)、[全模块](../../implemented/feature/2026-10-18-miuix-full-modules.md)及[悬浮修复](../bug-fix/2026-10-19-floating-chrome.md)部分重叠：保留五个目的地、完整依赖与选择契约；本篇替代运行时展示内容，不取代尚未实现的订阅、存储或Go任务契约。旧笔记与交接记录保留历史事实，不把历史实现描述当作当前运行时文案。

## Alternatives considered

- 仅删除“演示”标签或重命名假节点：改动小，但会使固定延迟看起来像真实测量，明确不采用。
- 同时删除真实测试：文件看起来更少，但会损失分类、选择、RTL安全区与主题的回归保护，用户也已选择保留测试命名。
- 趁清理一次实现所有后端：能填充页面，但涉及解析、安全存储、Go测速和生命周期，超出本次UI清理范围，不隐式扩张。

## Consequences

- commonMain不再有固定节点和固定测量结果；NodeSummary的latencyMs默认为空，唯一选择夹具位于commonTest。App当前目录与选择均为空，不伪造导入或结果。
- 已删除DemoNode.kt、DemoComponents.kt、SpeedTestScreen.kt、DemoSelectionTest.kt及对应旧入口；替代为NodeSummary/NodeSelection/ChoiceOption/SpeedScreen/NodeSelectionTest。生产命名守卫按单词识别，避免误伤Latest等正常名称。
- 展开状态、开发页脚、主题参数展品、演示能力说明和直接Compose animation声明已删除；运行时只有正式功能标题和正常空状态。
- 21项Python测试、结构、差异检查通过；两项生产边界守卫防止占位文件与假结果文字回流。共享测试源共11项，包含新增空目录和RTL留白验证，尚未执行Kotlin/双端编译。
- 订阅数据来源与节点测速核心仍未接入，开始按钮保持禁用。[设置与镜像](../feature/2026-10-19-settings-mirrors.md)在单独授权后增加设置持久化和独立镜像探测，不取代本篇的无假结果边界。清理不代表可发布；不改Git历史／签名，不自动推送或构建。
