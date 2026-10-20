# Agent Note: 设置表面层级与统一卡片

Status: implemented

## Problem

用户反馈默认主题中卡片与页面边界不清，批准逐项改造的第2项。iOS浅色当前为#F7F7F7页面和白卡片；Android动态配色中surface和surfaceContainer也可能很接近。外观页和镜像页直接使用Card，其余设置复用SettingsCard，不能只修首页漏掉子页。

## Decision

- 保留一个MiuixTheme根，主题根只派生页面、设置卡片、卡片前景和轮廓四个只读颜色；不新增配色算法或第二套全局状态。
- iOS浅色页面改#F0F1F3，卡片和前景仍用官方surfaceContainer/onSurfaceContainer；深色surface/surfaceContainer不变。
- Android浅色页面用Neutral生成的surfaceContainerHigh，卡片用surface/onSurface；深色仍用surface与surfaceContainer/onSurfaceContainer。只重新安排官方角色，不硬编码壁纸生成色。
- 复用SettingsCard承载所有设置页卡片；Card继续负责填充、裁剪和内容，轮廓用官方squircleBorder，宽0.5.dp，圆角沿用CardDefaults.CornerRadius=16.dp，浅黑4%/深白8%；不加阴影或第二次裁剪。
- 颜色仅通过CompositionLocal传递派生值，不保存副本，不替代ThemeController。Miuix的动态颜色标记和组件选择/禁用行为保留。
- 不改字号、边距、页内行为、镜像任务、存储或顶底材质；后续项仍待逐次确认。

## Existing decisions audit

- [动态来源主题](../../implemented/feature/2026-10-19-dynamic-neutral-theme.md)：继续系统代表色＋Neutral/Spec2025，仅补根主题装配与表面角色映射。
- [统一悬浮材质](../../implemented/bug-fix/2026-10-19-floating-chrome.md)：部分重叠；只改变页面捕获的底色，浮层材质参数和生命周期暂保留。
- [设置持久化](../../implemented/feature/2026-10-19-settings-mirrors.md)：保留全部持久化、草稿、取消、确认与错误流程。

## Alternatives considered

- 给全部卡片加阴影：能增加浅色立体感，但深色仍难区分，且用装饰代替稳定底色层次，不采用。
- 所有平台硬编码中性底色：结果容易预测，但丢掉Android动态配色的中性表面色相，不采用。
- 新建另一套主题或手画卡片：可完全控制，但Miuix已有Card、颜色角色和平滑描边，重复实现没有收益。

## Consequences

- 所有设置页原有Card均迁移到SettingsCard，包括外观、镜像排序/线路/编辑/删除确认；原有选择、网络、草稿和错误处理不变。SettingsCard透传官方ColumnScope，删除旧的额外Column，不增加平行实现。
- 全局页面底色和LayerBackdrop捕获底色一致，五页共享底色同步调整；其他一级页卡片和布局尚未迁移。MiuixTheme仍只在AppTheme内装配一次，保留controller与isDynamicColor语义。
- 新增3项Kotlin表面角色映射测试（未执行）；连同第1项配置测试，当前声明68项，CI最低门槛由62同步到68。新增Python统一卡片/采样接线守卫；28项Python、结构与diff检查通过。
- 原生编译、真机视觉和动态色边界验收待后续授权；Neutral具体颜色随系统代表色变化，0.5.dp轮廓在屏幕密度下的实际观感仍需验证，不能把静态颜色检查写成真机通过。
- 未运行前后原生渲染，本项只交付源码与静态证据。底色/卡片/轮廓值已同步[方案对照表](../../../../docs/settings-redesign.md)，后续调整必须更新表中值。
