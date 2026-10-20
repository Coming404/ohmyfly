# Agent Note: 材质选择与持久化默认液态玻璃

Status: implemented

## Problem

外观首页只有明暗模式和减少透明效果，尚无材质选择或独立存储字段。用户要求先实现6.1，默认液态玻璃；同时将后续6.3改为复用Melox的图文折射、玻璃胶囊与交互效果，并补iOS设备倾斜传感器适配。

## Decision

- 新增共享MaterialMode枚举与AppSettings.materialMode，按用户纠正的四项为高斯模糊、高光玻璃、柔光玻璃、液态玻璃，默认LiquidGlass；UI标签不作为落盘键。取消误加的渐进模糊和独立关闭项，实体显示由既有减少透明效果控制。
- 使用现有ChoiceSetting/OverlayDropdownPreference与AppSettings/onChange链，在外观组显示当前值及官方选择弹层，不新增局部业务状态或弹层宿主。
- 以ui.materialMode保存稳定枚举名；缺失、损坏或未知值在内存回退LiquidGlass，不因读取写回。新增可选key保持schema 1，旧配置的明暗、减少透明效果及业务参数保留，写入保留未知key。SettingsCodec的泛型读取复用inline reified安全类型检查，避免错误类型在返回后强转而越过原catch；read统一复用readOrNull。
- 减少透明效果控制原有背景采样/实体回退，但不覆盖已选材质。四项本轮只保存选择，仍使用现有模糊；6.3将共享Melox采样/折射/高光/交互能力，按预设启停和调整，不复制四套实现，也不把旧高斯当成液态基线已接通。
- 继续沿用保存成功后Flow更新界面的现有契约；6.2负责统一乐观反馈及失败恢复。本轮不改PersistedSettings的并发/失败处理。

## Existing decisions audit

- [即时反馈与保存恢复](2026-10-20-optimistic-settings.md)接管本篇阶段保留的“保存成功后更新”契约：单个会话快照同步反馈，原变换按序保存，失败丢弃后缀并回到已确认值。配置字段、官方菜单和schema 1不变。

- [设置持久化](../../implemented/feature/2026-10-19-settings-mirrors.md)：部分重叠，沿用DataStore事务、schema 1与只读回退，新增可选材质key。
- [首页结构](../../implemented/feature/2026-10-19-settings-home.md)：部分替代“不显示未接通材质入口”，现在增加真实可保存选择；其余布局、六子页与官方弹层保留。
- [底部导航](../../implemented/feature/2026-10-20-stacked-bottom-navigation.md)：不更改本项布局与参数；后续6.3按用户新授权加入Melox效果，不将其写为本轮交付。
- [统一悬浮材质](../../implemented/bug-fix/2026-10-19-floating-chrome.md)：保留原有减少透明效果与实体回退，四种材质本轮仍沿用原效果。
- proposed/rejected中无同主题决定；未来6.3的图文扭曲将替代原设计“仅折射背景”约束，具体范围见[视觉计划](../../../../docs/settings-redesign.md)。

## Alternatives considered

- 保持默认高斯模糊：与当前渲染一致，但用户明确要求默认液态玻璃，因此选择默认LiquidGlass，并明确配置与渲染分期交付。
- 本轮一并实现全部玻璃效果与即时状态：能立即看到完整差异，但超出本轮先实施6.1的逐项范围；不以配置接入代替效果验收。
- 提升schema并整表迁移：能标识新版本，但本轮仅增加可选key，默认解码已兼容旧数据；不增加拒读与破坏性迁移。

## Consequences

- 首页四项单选通过现有回调/持久化路径接线，默认液态玻璃；减少透明效果走原实体路径，选择不被降级改写。取消项来自本轮未发布实现，不保留兼容枚举或别名；其落盘字符串按既有未知值规则只在内存回退LiquidGlass。
- 新增6项MaterialSettingsTest覆盖四种往返、旧数据/未知/错误类型回退、只读回退、其他字段与未知key保留、减少透明效果保留选择；原SettingsCodecTest补非默认材质往返。共享测试总80项声明，CI门槛80，尚未执行。
- 计划6.3已改为Melox图文折射、玻璃胶囊/交互与iOS倾斜传感器适配；没有将未来传感器与渲染实现写入运行时代码。
- 四种材质现由[共享玻璃效果链](2026-10-20-melox-glass.md)驱动，[即时反馈](2026-10-20-optimistic-settings.md)处理保存前后状态；本篇配置默认与旧值迁移不变。
- 新增4项Python配置/菜单/存储/关闭接线守卫，累计53项通过；结构检查与git diff --check通过。初次结构检查在笔记仍处proposed目录时发现两处目标互链缺失，转入implemented后复验通过。
- Kotlin、DataStore实盘重启及双端视觉需后续授权构建和真机验证；不提交、推送或触发Actions。
