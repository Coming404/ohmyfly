# Agent Note: Melox共享玻璃采样、四材质与导航交互

Status: implemented

## Problem

6.1已保存四种材质，但两栏仍固定textureBlur。用户授权6.3，要求移植固定Melox 49ee06aa1f01a67eb5ea1d5cc7307ddfd571a534的图文采样、Lens/色散、BloomStroke和完整导航交互，而不是近似模糊。现NavigationBarShape为Generic Outline，上游Lens会静默跳过，BloomStroke还会以半高圆角误算边缘。

## Decision

- 复用现有Miuix 0.9.4的Backdrop、RuntimeShader、blur和HighlightStyle，commonMain不加依赖。保留页面Backdrop，导航另录制隐藏图文tabsBackdrop并CombinedBackdrop供胶囊采样。
- 原655行导航按绘制/采样、tabs布局语义、手势动画、追光职责拆分；不带播放器、Android宿主或另一套路由。四材质通过一份参数表驱动同一效果链。
- 使用Miuix addSquircleRect原有cubic轮廓，Lens和BloomStroke以相同cubic最近点SDF计算；保留平滑轮廓，不谎称CornerBasedShape可解决Generic Outline几何。clip、阴影、折射及高光共用同一形状。
- 真实返回/动作容器与底栏都读AppSettings.materialMode。[6.4无障碍与降级](2026-10-20-glass-accessibility.md)补齐应用/iOS系统减少透明效果共同优先，任一限制或运行时shader不支持时实体回退并停页面采样、图文录制与传感器；[第7项运动策略](2026-10-20-reduced-motion.md)将门控提升至AppMotion，应用reduceMotion或系统MotionDurationScale=0时停自有形变/橡皮筋及非必要追光，材质在原单链中作180ms数值插值。
- 只消费sensor agent的rememberGlassGravity(enabled): State<Offset>，draw阶段读重力、atan2与3度量化。

## Existing decisions audit

[材质配置](2026-10-20-material-selection.md)、[底栏布局](2026-10-20-stacked-bottom-navigation.md)、[悬浮材质](../bug-fix/2026-10-19-floating-chrome.md)部分重叠：前者配置/默认值保持，后两者布局和实体回退保留，固定高斯及仅背景采样由本项替代。主代理负责总docs与守卫集成，不更改旧笔记和主docs。

## Alternatives considered

- 直接把Shape伪装成CornerBasedShape能少改Lens，但圆角SDF与真实squircle不同，BloomStroke也会错边；不选。
- 采用普通圆胶囊完全复用上游shader最小，但丢失已确认平滑轮廓；不选。
- 四套材质各写效果可分别微调，但会复制采样与交互且容易叠加旧高斯；使用共享效果链和真实参数开关。

## Implementation and evidence

- `GlassNavigationSurface.kt` 将现有页面Backdrop与隐藏图文Backdrop接到移动胶囊；隐藏行没有输入、焦点或可访问动作。`GlassNavigationTabs.kt` 保留五入口、24dp/11sp/4dp与自然增长的64dp最小高度，真实行保留单一selectable Tab语义及Enter/Space激活。外层仍只应用一次safeDrawing、外16/底8/max560。
- `GlassNavigationGesture.kt` 只保存视觉拖动预览，不保存另一份committed selection。越过横向slop后消费拖动，防止释放同时触发tab点击；多指接续、取消回归、App外部导航同步、RTL映射、按压/速度形变、4dp橡皮筋和回弹均接通。初次显示不合成按压。
- App只新增读取`settings.materialMode`及shader能力门控，未更改PersistedSettings content签名、设置模型或存储。顶栏真实返回与动作容器和底栏均调用共享预设/效果链；胶囊在已经录制的材质图文之上只添加动态Lens，避免二次模糊。
- `rememberGlassHighlight` 实际调用sensor owner的`rememberGlassGravity(enabled && motionAllowed && highlightAlpha > 0)`；只在draw依赖中做atan2和3度量化。sensor的y-up只在映射灯位时转换为shader的y-down；没有写入sensor目录。
- 许可证与逐文件来源见`third-party/Melox-NOTICE.md`，GPL-3.0与Apache-2.0全文均落盘，保留原Apache/Kyant0头。许可记录不是发布或整个项目重新许可的批准。

## Testing

- 新增4个glass commonTest类，共22个`@Test`（预设/门控6、tab/RTL几何6、cubic形状6、重力量化4），不包含sensor owner的测试；本轮未运行Kotlin测试。
- `python3 tools/check_project.py` 已通过；`git diff --check` 已通过。初次全量Python为59项，57通过，玻璃头部归属守卫及CI @Test总量守卫失败；补齐文件归属后定向`test_glass_pipeline.py` 3/3通过。CI门槛需要由主代理按最终commonTest总数更新（该次快照为131、门槛仍109），本owner不修改Python/Gradle/CI。
- 所有本任务运行时代码文件均小于500行；未运行Gradle、CI、Xcode、Android/iOS截图、读屏/键盘实机或性能验收，静态通过不代表编译或实际视觉效果通过。

## Consequences

cubic SDF较圆角SDF昂贵，须双端真机核查帧耗和边缘精度；iOS RuntimeEffect已具备公共适配，但本轮不运行Gradle/Xcode，不能宣布编译或视觉通过。
