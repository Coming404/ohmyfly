# Agent Note: 设置首页与折叠单选

Status: implemented

## Problem

用户授权分项改造第3项：设置首页增加紧凑品牌区，整理真实入口，选择行具备明确展开方向和带标签单选。旧首页是三组描述性跳转列表，外观另占子页；ChoiceSetting无箭头，ChoiceOption用手绘圆框和对勾代替官方RadioButton。

## Decision

本篇的原地折叠及页面持有展开状态，已由用户图示纠正的[官方预设选择弹层](2026-10-19-preset-selection-popup.md)替代。品牌、六子页、表面层级与其他ChoiceOption调用方继续保留；以下原交互取舍作为被替代决定的历史依据。

- 首页使用现有Wave标记、实际平台version参数和已获准的单行简介；不加状态摘要或功能占位。
- 将明暗模式和减少透明效果移入首页外观组，复用现有AppSettings/onChange；删除无剩余用途的AppearanceSettingsPage及路由，不保留双入口。
- 测试、订阅与网络、应用分组保留六个复杂子页。跳转行使用官方ArrowRight图标，不把箭头拼进文字；只保留必要的当前值，无描述性摘要。
- ChoiceSetting统一折叠行为：初始收起、值变化后以现值为准、选中即关闭；展开状态由首页/测速设置页持有，声明在SettingsList之外，滚出视口再返回不因惰性列表项回收而丢失，离页后重新进入仍收起，不写入配置。官方图标上下方向，按钮展开状态语义，选项整行单选且官方RadioButton不重复占焦点。
- 复用BasicComponent内容槽定义16sp标题/14sp当前值，不重写其点击与布局。固定版内置最小高度56dp，保留它而非为原拟52dp重画控件。设置列表边距/间距16dp，品牌40dp/12dp间距。
- 第3项仅使用即时展开/切换；220ms高度/淡入淡出、180ms箭头及平台减少动态效果统一留第7项，不提前新增持久化字段或动画依赖。材质、顶底栏均不改。

## Existing decisions audit

- [材质选择默认液态玻璃](2026-10-20-material-selection.md)：扩展首页外观组与AppSettings字段，复用本篇迁移后的官方选择弹层；6.1只做配置与关闭路径，6.2/6.3分别处理即时反馈和实际玻璃效果。

- [设置NavDisplay接入](../architecture/2026-10-20-settings-navdisplay.md)：替代页面的单值分发/返回实现，品牌、六入口、弹出选择与唯一AppSettings保留，不重画首页。

- [设置与镜像](../../implemented/feature/2026-10-19-settings-mirrors.md)：部分重叠，仅替代七个子页入口的布局；持久化、校验、错误与真实网络边界保留。
- [设置表面](../../implemented/feature/2026-10-19-settings-surfaces.md)：继续复用SettingsCard和统一表面，字号与间距属于当前项。
- [生产清理](../../implemented/simplification/2026-10-19-production-ui.md)：保留不造假结果、无描述性装饰与不增加无效功能入口的约束。

## Alternatives considered

- 继续独立外观页：迁移更少，但仅两个控件多一层导航，与获准的首页外观区冲突，不保留双入口。
- 直接使用ArrowPreference：官方箭头与RTL自动处理最省代码，但0.9.4仅字符串标题、固定主题字号且不透传role/onClickLabel；采用其基础BasicComponent内容槽与同一官方图标，避免为定制再套平行点击行。
- 为52dp重写整个偏好行：数值完全一致，但重复官方布局与交互；保留官方56dp并记录目标修订。

## Consequences

- 品牌区接真实version，外观使用同一个onChange；六个业务子页保留，首页没有伪功能。旧AppearanceSettingsPage.kt、枚举与分支一并删除，不保留双实现。各新文件均少于100行，设置路由只负责分发；不增加依赖、存储字段、主题状态副本。
- ChoiceSetting复用于首页明暗模式和原测速默认模式；ChoiceOption换官方RadioButton后继续服务节点分类、镜像选择等既有调用方，单选容器仍持有唯一点击语义，尾件清空重复语义。
- 收尾复核确认LazyColumn回收可能丢失列表项内部remember状态，已将首页/测速页的展开状态提升到页面，并扩展原守卫检查声明在SettingsList之前、两处调用方均传入状态和回调。只读审查返回未形成完整结论，其余推测不计为已验证证据；原生读屏和视觉仍待设备验收。
- 本篇迁移时保持DataStore保存成功后通过Flow更新UI；该状态策略现由[即时反馈与保存恢复](2026-10-20-optimistic-settings.md)替代。首页结构与选择控件不变，乐观快照在统一PersistedSettings会话持有，不在首页增加副本。
- 新增5项Python接线/语义守卫，连同原测试34项通过；结构、差异检查通过。Kotlin测试仍68项声明未运行。本地静态检查不能证明Kotlin编译、读屏、字体缩放和视觉密度，原生构建与双端真机等待后续授权。
- 先用Miuix Skill组件/偏好指南，再以EXA MCP补足[固定版BasicComponent内容槽](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Component.kt)及[ArrowPreference](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-preference/src/commonMain/kotlin/top/yukonga/miuix/kmp/preference/ArrowPreference.kt)精确签名；官方RadioButton调用复用已有MirrorRow实现，不重复联网。官方56dp与应用16/14sp、16dp间距已同步[方案对照](../../../../docs/settings-redesign.md)。
