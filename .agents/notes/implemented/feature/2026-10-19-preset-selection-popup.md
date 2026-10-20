# Agent Note: 预设选择使用官方弹出菜单

Status: implemented

## Problem

用户以截图纠正第3项交互：没有下级设置、只从已有值中选择的设置行，使用右侧常驻上下双箭头和选择弹层，不在页面内展开单选区。主题切换是明确示例。原实现把图片的双箭头误解为上下切换的展开指示。

## Decision

- 主题和测速默认模式复用已有miuix-preference的OverlayDropdownPreference；ChoiceSetting只映射稳定ID与标签/索引，不自建弹窗、选中副本或动画。
- 使用官方DropdownArrowEndAction（MiuixIcons.Basic.ArrowUpDown，10x16dp），常驻双箭头；当前值显示在行右。菜单当前项使用官方对勾，单选后自动关闭。
- 页面内只保留行，不插入单选列表；删除旧展开属性、首页/测速页的局部展开状态与SettingsActionRow的展开旋转分支。复杂子页仍使用右向跳转箭头，开关仍为Switch。
- 复用已有根Miuix Scaffold的Overlay宿主，菜单/返回/遮罩与进出场由上游处理，不增加Window、嵌套Scaffold、BackHandler、动画库或第二层AnimateVisibility。
- 偏好行采用官方headline1/body2字体与默认16dp内边距、BasicComponent 56dp下限；不为保持应用原16sp标题而给单个控件套平行主题。跳转/开关行的应用16sp/14sp与16dp列表间距不改。
- 本次只迁移已存在的两个单选字段；不添加语言/主页字段，不把有多项候选误解为业务字段必须多选。将来真实多选使用官方entry/entries或平面菜单，不引入级联children。

## Existing decisions audit

- [设置首页与折叠单选](../../implemented/feature/2026-10-19-settings-home.md)：部分替代，保留品牌/六子页/官方RadioButton的其他调用方和卡片；替代原地折叠决定及其状态提升修复。
- [设置与镜像](../../implemented/feature/2026-10-19-settings-mirrors.md)：存储、校验及镜像任务不变。

## Alternatives considered

- 原地折叠：可同时看到全部选项，且已完成状态提升；但与用户本次图示及明确纠正冲突，删除而非保留备用。
- Spinner对话框或BottomSheet：能承载图标/摘要/很多选项或表单，但目前主题/测速仅三个文字选项，空间和交互都过重。
- 手绘双箭头加自定义弹窗：可精确覆盖16sp，但官方组件已实现当前值、选择语义、弹层与动画，重复实现没有收益。

## Consequences

- 两个调用方仍更新同一配置，不丢选项，默认值不变；原地展开状态/列表及导航行的旋转兼容路径已删除。ChoiceSetting只做稳定ID/标签索引映射，不增加依赖、配置字段、宿主或主题副本。
- 原5项首页接线守卫改为官方选择器/根Scaffold/ID映射/无展开状态残留检查；与其余测试共34项，结构和diff检查仍作为本地验收，Kotlin声明68项未运行。
- 先读Miuix Skill偏好/弹层指南，再用EXA MCP固定commit补证OverlayDropdownPreference和Dropdown.kt的签名/常驻ArrowUpDown（10x16dp）。固定路径demo读取未命中，未把它当成功示例；实际参数依据同版公开源码。菜单使用上游内置进出场，不擅定动画时长。
- [即时反馈与保存恢复](2026-10-20-optimistic-settings.md)替代本篇阶段保留的保存后更新契约；官方菜单的选中值继续只读同一AppSettings，不增加菜单状态副本。原生编译、位置、系统返回优先级、读屏、大字体及最低系统动画仍需设备验收；本篇的本地证据不代替运行时验证。
