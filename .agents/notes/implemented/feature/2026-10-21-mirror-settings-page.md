# Agent Note: 第 8 项 GitHub 镜像页按线框还原

Status: implemented

## Problem

原镜像页是整页编辑、行内管理和顶部测试操作，与九张独立图片及流程总览不一致；还把乐观列表相等误当保存完成。用户在实施中确认：普通拼接行不套卡片、底部手动保存、镜像支持 HTTP(S)／裸域名、无直连条目、空列表纯图标文字提示。

## Decision

- [局部规格](../../../../docs/design/mirror-settings/PRD.md)和 R2 图片提供视觉基准，最新用户决定优先。主列表复用 SettingsCard／ToggleSetting；每项唯一 ⋯、右侧状态、卡头无排序切换图标；底部单一批量测试按钮。返回／新增共用 chromeMaterial，内容只保留一次标题。当前精修与例外见[镜像页交互精修](2026-10-22-mirror-page-polish.md)。
- 一个始终保留组合的 MirrorDialog 使用官方 OverlayDialog、原根 Scaffold、居中模式、无灰幕；其表面使用默认 DialogDefaults 背景，不复用 chromeMaterial。下方正文和顶栏2dp模糊持续至 onDismissFinished；快速重开不提前撤除。不新增导航路由。
- MirrorPageState 只拥有弹窗和会话草稿，MirrorDraftState 保留 TextFieldValue 游标／合成态，不是第二设置 Store。拼接方式使用官方 OverlayDropdownPreference，已有 Template 仅显示禁用项，可显式切换前缀。
- 新增／编辑只有底部手动保存；输入、失焦、键盘完成、关闭不保存。保存中防重复，Session.changeConfirmed 共用原顺序事务并返回 Confirmed／Failed／Uncertain／Rejected；真实 update＋读回且候选一致才关闭。confirmedSettings 不等于乐观 settings，探测只拿确认值。失败后缀丢弃、草稿保留；写后读失败保留页面，retryRead 仅重读且防重入。
- HTTP／HTTPS 显式保留，裸域名补 HTTPS，www不增删，路径大小写保留；normalize／dedupe／preview 使用同一规则。旧 Template 不静默转换。传输和同源跳转支持 HTTP，HTTPS→HTTP 拒绝；原始 GitHub 与其他设置 URL 仍要求 HTTPS，公网DNS／凭据／重定向／大小／摘要限制不削弱。
- 开关关闭只直连；开启先指定镜像、其余持久化顺序、全部失败后才直连。无指定／旧选择失效从首项开始。列表与批量测试去直连；删当前项改选剩余首项，删光与关闭镜像同一事务。空列表无卡片／无新增按钮，只 GitHub 图标＋Bold“暂无镜像源”；右上 ＋ 保留。
- Android cleartext、iOS ATS 放开用户动态 HTTP 域名；镜像应用层仍严格校验，HTTP 本身不提供加密。iOS设置输入框局部覆盖已核实公共 TextToolbar，隐藏系统浮动编辑菜单，不改 Android／只读预览；未用私有API或全局 UIKit swizzling。

## Existing decisions audit

- [原镜像](2026-10-19-settings-mirrors.md)、[导航](../architecture/2026-10-20-settings-navdisplay.md)、[即时反馈](2026-10-20-optimistic-settings.md)：部分替代界面／返回／确认信号，仍复用 DataStore、事务、网络安全与单栈；旧当前使用／路线检查卡和整页编辑返回补丁删除，资源降级核心保留。
- [真机反馈提案](../../proposed/feature/2026-10-20-device-feedback-rework.md)：只接管第8项镜像局部，其余范围仍 proposed，不能把本项记作全部反馈完成。
- [官方选择菜单](2026-10-19-preset-selection-popup.md)：其他设置菜单不变；镜像单选项需要特殊材质／父弹窗外点边界，采用普通设置行＋单层覆盖区。

## Alternatives considered

- 仅调现有整页编辑的样式：改动少，但无法得到图中的模态替换与底部固定操作。
- 自动保存／关闭时提交：减少按钮，但用户已明确改为手动保存，不保留并行隐式路径。
- 编辑弹窗嵌套 OverlayDropdownPreference：自带状态语义，但单选项会增加模态堆叠和遮罩边界；复用基础行、同层选项更直接。
- 维持仅HTTPS：默认更安全，但不满足用户明确HTTP输入需求；开放平台运输限制，同时保持应用层只读公开镜像边界，不通过放松原始GitHub或其他URL规则实现。

## Consequences

源码组件按UI／页面草稿／Session／领域／运输边界拆分，单文件均低于300行；Session逻辑超过200行，职责仍是同一顺序事务与确认生命周期，继续保持内聚而不增空壳接口。无新增依赖。图标仅补库中没有的本地Lucide路径，许可保留于设计licenses并在文件头标来源。

新增回归覆盖手动保存／关闭保稿、确认与后缀失败、未知结果重读、地址规范化／公网拒绝、镜像失败后直连、删光关闭、停止互斥；共享测试声明174项、CI门槛同步。Kotlin／双端构建及设备渲染未执行；结构检查、73项Python轻量检查和git diff --check通过。iOS文字菜单实际显示、键盘／大字／读屏焦点、四材质视觉及公网HTTP请求仍需原生验收，静态守卫不能替代。
