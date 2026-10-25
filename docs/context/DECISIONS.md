# DECISIONS

已确认决策的权威来源。建议与提案不是决定。

## 记录规则

- 2026-10-23 起，新决策直接写在本页“新决策”节，每条一行：日期、决定、理由、范围、来源。需求本身写在对应需求文档，本页只记“为什么这么选”。
- `.agents/notes/` 来自已停用的 skill，**不再新增或更新**，仅作 2026-10-18 至 2026-10-22 的历史决策存档，按需检索。其格式仍由 `tools/check_project.py` 校验，不要改动。
- 决策被替代时，在本页把旧条目标为“已替代”并链接新条目；替代历史存档中的决策时，在新条目里写明被替代的记录。

## 新决策

| 日期 | 决定 | 理由 | 范围 | 来源 |
| --- | --- | --- | --- | --- |
| 2026-10-23 | 改名 OhMyFly，包名 `com.ohmyfly`；订阅→塔台、节点库→航班、测速→起飞；不保留旧本地数据 | 用户产品命名决定 | 全项目 | 用户，需求见 [U5–U9](../device-feedback-rework.md#81-条目) |
| 2026-10-23 | 删除所有不需要、过时及尚未实现功能的配置与兼容逻辑（含 Template 模式、未知 key 保留、`mirror.order` 清理） | 用户要求不保留无用代码 | 设置持久化与镜像 | 用户，[U11](../device-feedback-rework.md#81-条目) |
| 2026-10-23 | 启动不显示配置加载界面；读取失败改用图标＋文字＋按钮弹窗 | 用户要求 | 启动流程 | 用户，[U10](../device-feedback-rework.md#81-条目) |
| 2026-10-23 | applicationId 与 bundle id 为 `com.ohmyfly.app`；动画数值与 shared namespace 由实施方选定，以人眼明显感知为准 | 用户授权实施方决定 | 改名、运动 | 用户，[U1、U6](../device-feedback-rework.md#81-条目) |
| 2026-10-23 | iOS 输入框：单击聚焦不弹工具栏，双击/长按显示系统默认工具栏 | 现有实现全部隐藏，无法复制粘贴 | iOS 设置输入框 | 用户，[U12](../device-feedback-rework.md#81-条目) |
| 2026-10-24 | U11 不删除设置页内已有的子页与配置项（起飞、流媒体与 AI、塔台与过滤），只删旧格式兼容逻辑 | 用户决定保留已有设置界面 | 设置持久化 | 用户，[U11](../device-feedback-rework.md#81-条目) |
| 2026-10-24 | 镜像编辑器保留“拼接方式”行，改为只读展示“HTTP(S) 前缀”，不再弹下拉；U3/AC-M12 照常 | 该行是 U3 已确认的可见控件，不属旧兼容 | 镜像编辑器 | 用户，审查报告 20261024-reviewer-persistence |
| 2026-10-24 | 读取设置失败弹窗沿用 Overlay 系列，放进 Scaffold 内显示；与“写入结果未确认”分开提示 | Overlay 依赖 Scaffold 宿主，放外面不显示且恒浅色 | 启动流程 | 用户，[U10](../device-feedback-rework.md#81-条目) |
| 2026-10-25 | 镜像编辑器“名称”“拼接方式”“地址”三个标题统一为 13sp Normal（改写 AC-M12 原定 16sp）；失败弹窗拦截返回手势，不播缩小动画 | 用户指定 13sp；不可关闭弹窗完成预测返回后 Miuix 不复位缩放/遮罩 | 镜像编辑器、启动流程 | 用户，审查报告 20261025-reviewer-persistence-fix |
| 2026-10-25 | 范围扩大：“名称”“拼接方式”“地址”“改写预览”四个标题及“HTTP(S) 前缀”值统一 13sp Normal，文字颜色由灰色（onSurfaceVariantSummary）改为主题前景色 onSurface；标题图标保持灰色；预览框内改写结果正文不变 | 用户指定 | 镜像编辑器 | 用户 |

## 长期约束（来源：[AGENTS.md](../../AGENTS.md#ohmyfly--开发指引)）

最低系统版本、Miuix Scaffold、Go 负责起飞、云端构建、依赖须先查 Klibs 等长期约束只在 AGENTS.md 维护。

## 历史决策存档（`.agents/notes/`，只读）

### 已实施

| 主题 | 记录 |
| --- | --- |
| 工程基础 | [project-foundation](../../.agents/notes/implemented/architecture/2026-10-18-project-foundation.md) |
| 设置 NavDisplay | [settings-navdisplay](../../.agents/notes/implemented/architecture/2026-10-20-settings-navdisplay.md) |
| 五入口与导航 | [five-destinations](../../.agents/notes/implemented/feature/2026-10-18-five-destinations.md)、[stacked-bottom-navigation](../../.agents/notes/implemented/feature/2026-10-20-stacked-bottom-navigation.md)、[floating-chrome](../../.agents/notes/implemented/bug-fix/2026-10-19-floating-chrome.md) |
| Miuix 模块与资料优先级 | [miuix-full-modules](../../.agents/notes/implemented/feature/2026-10-18-miuix-full-modules.md)、[miuix-evidence-priority](../../.agents/notes/implemented/process/2026-10-19-miuix-evidence-priority.md) |
| 主题与配色 | [expressive-overview](../../.agents/notes/implemented/feature/2026-10-18-expressive-overview.md)、[dynamic-neutral-theme](../../.agents/notes/implemented/feature/2026-10-19-dynamic-neutral-theme.md) |
| 页面与展示数据 | [ui-showcase](../../.agents/notes/implemented/feature/2026-10-18-ui-showcase.md)、[compact-page-header](../../.agents/notes/implemented/feature/2026-10-19-compact-page-header.md)、[production-ui](../../.agents/notes/implemented/simplification/2026-10-19-production-ui.md) |
| 设置 | [settings-home](../../.agents/notes/implemented/feature/2026-10-19-settings-home.md)、[settings-surfaces](../../.agents/notes/implemented/feature/2026-10-19-settings-surfaces.md)、[preset-selection-popup](../../.agents/notes/implemented/feature/2026-10-19-preset-selection-popup.md)、[optimistic-settings](../../.agents/notes/implemented/feature/2026-10-20-optimistic-settings.md) |
| 玻璃材质 | [melox-glass](../../.agents/notes/implemented/feature/2026-10-20-melox-glass.md)、[material-selection](../../.agents/notes/implemented/feature/2026-10-20-material-selection.md)、[glass-gravity-sensor](../../.agents/notes/implemented/feature/2026-10-20-glass-gravity-sensor.md)、[glass-accessibility](../../.agents/notes/implemented/feature/2026-10-20-glass-accessibility.md) |
| 运动策略 | [reduced-motion](../../.agents/notes/implemented/feature/2026-10-20-reduced-motion.md) |
| GitHub 镜像 | [settings-mirrors](../../.agents/notes/implemented/feature/2026-10-19-settings-mirrors.md)、[mirror-settings-page](../../.agents/notes/implemented/feature/2026-10-21-mirror-settings-page.md)、[mirror-page-polish](../../.agents/notes/implemented/feature/2026-10-22-mirror-page-polish.md)（最新） |

### 提案（未确认为决定）

| 主题 | 记录 |
| --- | --- |
| 双端技术基础 | [mobile-foundation](../../.agents/notes/proposed/architecture/2026-10-18-mobile-foundation.md) |
| 私有仓库与 Release 构建 | [release-device-builds](../../.agents/notes/proposed/process/2026-10-18-release-device-builds.md) |
| 真机反馈修正 | [device-feedback-rework](../../.agents/notes/proposed/feature/2026-10-20-device-feedback-rework.md) |

待核实：前两项提案的内容已在工程中落地（工作流、私有仓库已存在），状态是否应改为 implemented 需用户确认。
