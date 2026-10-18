# Agent Note: 共享 Miuix 交互展示界面

Status: implemented

## Problem

目前只有品牌标题和居中占位文字，无法判断 Miuix 的卡片、导航、选择状态、主题和动效是否符合产品方向。用户要求先做基础 UI，而不是接入订阅或真实测速。

## Decision

用共享 Compose/Miuix 实现「概览、节点、外观」三个底部导航目的地，顶部始终显示 ohmydrift 品牌。展示品牌卡、统计卡、卡片列表、带图标的按钮、分类筛选、多选和文字层级。演示节点与数值明确标识，不发送网络请求。

状态由共享入口持有，切换导航不清除分类、多选与外观设置；状态仅会话内有效，不引入持久化、导航框架、DI 或数据库。动效使用现有 Compose 同版本的官方 animation 模块，提供减少动态效果开关，不加入自动循环、模糊和 Material 组件库。

## Review follow-up

独立审查收尾采用有名称的 ThemeMode 枚举，避免外观列表顺序与入口数字分支隐式耦合；颜色对象按实际明暗缓存。分类与主题使用 selectableGroup / RadioButton 单选语义，标签不拼接勾选字形；父行统一承载复选和开关语义，尾部视觉控件不重复发布状态。统计数值从演示数据派生，空分类有明确空态。

当前 Actions 只执行轻量 Python 检查及双端编译，不会自动执行 commonTest；尚未执行的 Kotlin 测试不算通过。不在未验证任务名/runner 条件下盲目扩充工作流。系统级减少动态效果的额外平台桥接、读屏及大字体截图仍待真机验收。

## Alternatives considered

- 保留单页并堆叠全部控件：代码最少，但无法观察底部导航与独立页面层级，不符合本次要求。
- 引入导航框架和完整图标包：更适合复杂路由和大图标目录，但三个本地目的地不需要；采用本地选择状态与少量共享矢量图标。
- 另写 HTML 或 iOS 原生展示：可快速在浏览器查看或获得原生效果，但会形成不参与实际 App 的平行实现，不采用。

## Consequences

- 三个目的地共用 Miuix Scaffold、主题与品牌顶栏；应用 Scaffold 的内容边距并消费 insets，不重复添加底部手势区边距。实际安全区与大字体效果仍需双端验收。
- 分类批量操作只改变可见项，其他分类选择保持；有明确的全局清空。纯逻辑的 6 个 commonTest 覆盖筛选、增删、跨分类全选、空集合、数据标识与三种主题模式，尚未运行 Kotlin 测试。
- 浅色、深色、跟随系统可切换；自定义页面过渡 180ms、卡片尺寸过渡 240ms，可通过设置跳过，不声称 Miuix 全局无动画。
- 本地项目检查与 4 个 Python 检查器测试通过；未运行完整 Gradle、Xcode 或真机检查，不生成未经构建的效果截图。
- Miuix 0.9.4 的底栏使用 `NavigationBar { NavigationBarItem(...) }`；同标签旧文档的 `items/selected/onClick` 示例不适用。旧 `extra.SuperCheckbox/SuperSwitch` 已移除，采用基础 Checkbox/Switch 配合可访问整行 toggleable，不增加 preference 模块。
- 动画模块与现有 Compose 对齐 1.12.0，Klibs/POM/目标变体核验见 [依赖记录](../../../../docs/research.md)。实现说明和真机步骤见 [UI 展示](../../../../docs/ui-showcase.md)。

## Risks

Miuix 0.9.4 API 已按锁定标签源码核对，但静态检查不能证明 Kotlin 编译或最低系统兼容。已有 [基础工程笔记](../architecture/2026-10-18-project-foundation.md) 与 [整体架构提案](../../proposed/architecture/2026-10-18-mobile-foundation.md) 只部分重叠：分别保留，本文只负责 UI 展示行为，不把 Go 或构建能力标记为已完成。
