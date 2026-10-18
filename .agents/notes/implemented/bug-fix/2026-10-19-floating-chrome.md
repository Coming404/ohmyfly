# Agent Note: 五页统一悬浮材质与系统动态配色

Status: implemented

## Problem

用户在9930858的iOS预览反馈：节点库底栏后出现整片白底；品牌栏是普通全宽不透明栏而非左上悬浮；顶底材质不一致、模糊不明显，滚动略卡；固定蓝色种子主题单调。用户选择保留共享Compose/Miuix优化，而非重写原生iOS，并选择系统动态配色。

源码可证：仅概览将Scaffold边距传给列表contentPadding并提供背景采样，其余四页在外层padding缩小视口；只有概览顶栏启用25f+80%表面色覆盖，底栏未模糊。全局Crossfade同时绘制新旧两页，不能据此量化掉帧，但会增加切页期间绘制工作。未找到滚动偏移驱动的每帧重组，4条演示数据不是大列表瓶颈。

## Decision

- 保留Miuix Scaffold、五个目的地、共享状态、完整模块/图标及系统下限。品牌改为安全区内左上角紧凑悬浮胶囊，页面操作仍跟随当前目的地；底栏保持Miuix悬浮胶囊。
- 所有页面使用同一全屏内容采样源；ChromeMaterial统一半径20f、浅色0.22/深色0.28覆盖、noiseCoefficient=0f和50dp圆角，内容卡片不玻璃化，外围透明。降采样由上游内部处理，没有臆造downsampleFactor参数。不复制复杂折射shader，不声称原生Liquid Glass。
- 五页都将Scaffold边距加入LazyColumn.contentPadding，不以外层padding裁切列表视口；封装小型纯边距合并函数并覆盖LTR/RTL及底部安全区。
- 取消全局双页Crossfade，底栏直接切换，仅绘制当前页；独立滚动状态、稳定key及选择语义保留。后续生产清理删除展开体验，减少透明效果选项令顶底共同退回不透明表面并停止内容捕获，不承诺关闭Miuix内部全部动画。
- Android采用MonetSystem且不传固定keyColor/style/spec，跟随系统配色；iOS无Android壁纸API，回退Miuix默认浅深色，不用固定紫色种子伪装壁纸色。宿主显式传递该平台能力，不改变共享UI架构。

## Related decisions

已检索活跃笔记：与[五入口导航](../../implemented/feature/2026-10-18-five-destinations.md)和[概览主题](../../implemented/feature/2026-10-18-expressive-overview.md)部分重叠；新悬浮布局替代全宽SmallTopAppBar，新系统色策略替代Vibrant蓝种子，新统一材质替代概览单页模糊。五页业务边界和全模块要求保留，不新增导航框架或订阅/测速后端。

## Alternatives considered

- iOS原生SwiftUI/UIKit导航材质：更直接获得系统Liquid Glass，但用户选择继续共享界面，不擅自改架构。
- 仅给旧底栏叠加模糊：改动小，但被缩小的内容视口仍无可采样内容，白带与浮层关系未修，不能解决根因。
- 保留固定蓝种子并改Vibrant参数：跨端可复现，但与用户本轮选择的系统动态色冲突，不采用。
- 维持180ms全局Crossfade：切页更柔和，但存在双页绘制与旧采样过渡；本轮优先简化切页工作，不用盲加缓存/限帧掩盖问题。

## Consequences

- 五页均使用withPageSpacing合并contentPadding，不缩小列表视口；Scaffold外层透明，只有小胶囊绘制材质，品牌位于安全区TopStart。大字体使用可收缩的品牌列，操作触区48dp保留。
- 上游固定tag核验：FloatingNavigationBar.modifier实际作用于胶囊；color=Transparent不覆盖模糊；iOS内置36dp底部留白不重复叠加。textureBlur接受Backdrop、半径和noiseCoefficient，不暴露手动降采样参数。
- rememberLayerBackdrop上游已用rememberUpdatedState更新onDraw，不按surface重建整个捕获层；仅共享捕获，不声称两个消费端共享一次模糊结果。主题/双消费坐标仍需真机确认。
- 结构、21项Python测试与差异检查通过。新增PagePaddingTest两项验证LTR/RTL和浮层留白；本轮共11项共享测试尚未执行，未运行Kotlin/Xcode或真机Profiler，不虚构性能改善百分比。
- 共享模糊仅为视觉近似，新增底栏材质仍有GPU成本，空状态背景也不会凭空产生丰富玻璃纹理。实机验收步骤见[UI实现](../../../../docs/ui-showcase.md)。不自动提交、推送、删除Actions或构建。
