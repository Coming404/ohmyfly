# Agent Note: 应用级减少动态效果与单链材质过渡

Status: implemented

## Problem

第6项的玻璃仅观察平台MotionDurationScale，设置尚无应用级持久化选择；主题与材质立即跳变。需要将应用限制和系统限制合并，并在运行中切换时取消非必要形变、追光与过渡，同时保留导航、点击、返回和设置回滚行为。

## Decision

- AppSettings增加默认false的reduceMotion，可选布尔key `ui.reduceMotion`，schema保持1；缺失/错类型只内存回落，原子写入和失败回滚复用SettingsSession。首页增加单独开关，不改用户材质选择。
- 根AppMotion组合应用偏好与Compose平台可观察的MotionDurationScale。系统为0或应用开启时禁止项目自有运动；正常模式不覆盖系统正数倍率。两端原生系统联动由Compose已有实现负责，不重复安装平台观察者或写系统设置。
- 主题53个Miuix语义色及4个应用表面色正常180ms过渡，减少动态直接终值并取消旧effect；材质在既有一条GlassPreset链中对8个数值参数插值，liquidResponse交互策略立即取目标。实体降级立即停采样，无双UI、双shader或背景交叉渲染。
- 保留官方controller根以提供内部动态主题标记；其内仅用一次官方colors重载覆盖显示颜色，并提供动画中的LocalContentColor。只用colors根会丢失Miuix.isDynamicColor信息，因此这是有实际兼容原因的颜色覆盖，不是第二控制器、Scaffold或页面树；颜色生成仍只由原ThemeController负责。
- 玻璃手势在策略/布局条件改变时仅替换keyed gesture owner，取消旧scope中的弹簧/光斑/回弹；测量宽度放在key外，唯一提交选择留在App。取消只恢复当前选择，不合成点击；减少动态的release直接snap，避免隐藏弹簧。Chrome光斑scope也随启用分支释放，倾斜采集继续经原enabled门控停止。
- 设置导航在限制下使用官方NavTransitions.None/NavDisplayEffects.None并关闭空间滑退；普通模式仍是MiuixDefault和方向化滑退。标题/系统返回、镜像编辑优先级、草稿与唯一栈不改，不靠重建NavDisplay清运动。库内部深度驱动不承诺当帧取消：本项保证移除可见空间变换，设备仍需核对返回结束与entry清理。
- **明确未实现的覆盖**：应用开关不替换根协程上下文，因此不能禁用所有Miuix内置Switch/Dropdown等组件动画；这些继续遵循系统运动限制。固定版iOS公开宿主没有上下文注入口，不使用internal API、不为此改写宿主。第7项设备整体交互和全组件应用级禁用不能标成完成。

## Related decisions

- [设置路由](../../implemented/architecture/2026-10-20-settings-navdisplay.md)：部分重叠，保留唯一栈/返回优先级，本项补运动策略。
- [动态主题](../../implemented/feature/2026-10-19-dynamic-neutral-theme.md)：部分重叠，保留取色与生成，本项只补过渡。
- [Melox玻璃](../../implemented/feature/2026-10-20-melox-glass.md)、[传感器](../../implemented/feature/2026-10-20-glass-gravity-sensor.md)、[无障碍降级](../../implemented/feature/2026-10-20-glass-accessibility.md)：部分重叠，保留原效果链/平台边界，本项统一限制与状态切换。
- 活跃笔记中没有同目的未落地提案需要删除或拒绝。

## Alternatives considered

- 只给玻璃传布尔值最少改动，但主题过渡和页面空间转场仍会运动；共享AppMotion策略并逐一接到实际拥有的调用方，官方内置动画的应用级覆盖则明确保留缺口。
- 给一个rememberCoroutineScope自定义MotionDurationScale看似最轻，但只影响该scope启动的协程，不能覆盖子组件LaunchedEffect，不将其伪装成全局开关。
- Android窗口工厂/自定义iOS宿主可扩大根上下文控制，但前者需internal opt-in，后者超出公开配置接口且增加生命周期/输入/渲染维护风险；保留平台宿主，不因一个开关扩架构。
- 只用MiuixTheme(colors)省一层颜色覆盖，但丢失内部LocalColorSchemeMode；保留官方controller根及继承该标记的colors覆盖，不访问internal变量或复制MiuixTheme实现。
- 主题/材质Crossfade两棵UI容易复用，但重复采样、重型玻璃与语义树增加成本；采用单树颜色/单链参数过渡。
- 写系统全局动画设置可影响更多组件，但越过应用权限和用户意图，不采用。

## Evidence

- [Miuix主题公开重载](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/MiuixTheme.kt)：controller重载提供动态标记与LocalContentColor，colors重载不覆盖模式；后者可公开消费插值Colors。全部53字段来自同提交Colors构造器。
- [MotionDurationScale官方API](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale)及[Compose1.12动画逐帧读取](https://github.com/JetBrains/compose-multiplatform-core/blob/f29d2f99f3beafc60992c6a73dbbaa9f841c5d4c/compose/animation/animation-core/src/commonMain/kotlin/androidx/compose/animation/core/SuspendAnimation.kt)：0时后续帧终止标准动画，正数倍率保持，不是普通CompositionLocal。
- [Compose1.12 iOS容器](https://github.com/JetBrains/compose-multiplatform-core/blob/f29d2f99f3beafc60992c6a73dbbaa9f841c5d4c/compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/scene/ComposeContainer.ios.kt)：已有UIKit减少动态接入，公开ComposeUIViewController配置不提供effectContext。固定版Android生命周期recomposer有上下文参数，但全局窗口工厂为InternalComposeUiApi。
- [Miuix NavDisplay](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-nav/src/commonMain/kotlin/top/yukonga/miuix/kmp/nav/core/NavDisplay.kt)：animateTopTo的effect只以target为key，None是公开视觉预设，不把修改预设说成重启/取消内部驱动。

## Testing

- 新增13个Kotlin声明：偏好兼容4、即时/回滚1、策略1、主题/表面插值4、材质插值3；累计146，CI门槛146。本轮未执行Kotlin/Gradle/Xcode或设备测试。
- 新增6项Python守卫覆盖持久化/唯一状态、系统scale不覆盖、限制取终值/导航None、手势scope与测量、53颜色字段及模式继承、单材质链。完整70项Python通过，结构/链接/笔记格式与git diff --check均通过。
- 原有底栏/玻璃/导航/表面色守卫迁移至新入口，不保留旧rememberGlassMotionAllowed路径；原创GlassPresetAnimation不冒充上游移植文件。
- 独立只读源码复核未发现本轮需要追加修复的明确问题，确认应用/系统门控、手势owner取消与宽度保留、完整色角色和原设置回滚接线。复核没有执行Kotlin编译或设备测试；导航None仅移除可见变换，内部驱动及返回/草稿运行表现仍按上述边界验收。

## Consequences

显示策略覆盖自有链路，官方控件的应用级全局禁用保持未实现，不因此新增依赖或改双端宿主。所有新增文件低于300行，动画helper只负责显示插值，无第二业务Store或导航状态。

主题中间色对比、连续改选与失败回滚、系统倍率、后台改限制后恢复、手势中途切换/草稿/焦点、倾斜停止及帧耗均待设备验证，见[运动矩阵](../../../../docs/ui-showcase.md#第7项运动设备矩阵)。无提交、推送、构建或安装包下载。本项完成的是列明边界的源码与轻量交付，不把整体设备验收或全组件禁用当已完成。
