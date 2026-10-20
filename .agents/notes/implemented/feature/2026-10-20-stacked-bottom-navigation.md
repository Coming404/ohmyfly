# Agent Note: 紧凑的五入口底部导航

Status: implemented
Date: 2026-10-20

## Problem

用户已授权从第4项进入第5项。现有底栏调用Miuix FloatingNavigationBar/Item，沿用项目50dp普通圆角和默认内容布局；尚未明确做到五项文字常显、上图下文、选中加粗及24dp平滑轮廓。第4项的紧凑标题已经完成，本项不再修改顶部。

## Decision

- 保留AppDestination顺序、App唯一destination及回调、设置子页隐藏底栏、根Scaffold与单一Backdrop，不改业务或路由架构。只替换DriftNavigationBar内部布局和选择项。
- 已经Skill优先、EXA固定commit补证：FloatingNavigationBarItem实际只显示图标，无文本内容槽；FloatingNavigationBar.content无RowScope，底部固定iOS36dp/Android26dp＋系统栏无法取消。标准NavigationBarItem虽有常显文字及选中Bold，但内部固定64dp高度，无法自然容纳大字体；不是为了2dp图标差异而重写。
- 应用局部Box/Row/Column以Compose标准selectableGroup/selectable(Role.Tab)负责点击和选中语义，Miuix Icon/Text/主题继续复用。图标24dp、文字11sp/14sp行高、上下间距4dp，Medium/Bold、五等宽；选中用primary，未选用onSurfaceVariantSummary，按下alpha0.6。文本单行可省略，完整标签仍供读屏，装饰图标不重复命名；文字已常显，不保留原TooltipBox包装。
- 主体最小64dp，非固定高度，跟随字体放大增长。一次safeDrawing底部/横向处理后，左右16dp、底部8dp；宽屏最大560dp居中，容器内左右4dp。上述均是应用设计值，不冒充Miuix默认。
- 新增22行NavigationBarShape只把官方Path.addSquircleRect（半径24dp，extension沿用1.1）适配成Compose Shape。ChromeMaterial仅增加可选shape参数，同一形状用于现有阴影、裁剪、textureBlur及边框；顶部默认50dp普通圆角不改。无自写几何/Shader或额外离屏遮罩。
- 模糊20f/噪声0/浅0.22深0.28底色/8dp阴影/0.5dp描边与减少透明实体降级均不变。不新增依赖、状态字段、配置、动画库或第二套全局状态；材质选择/折射/持久化/平台减少动态效果仍留第6/7项。

## Alternatives considered

- 完全保留FloatingNavigationBar/Item最有利于少改代码和沿用官方交互，但它没有可见文字/内容槽及可配置底部留白，不符合已确认需求。
- 自建悬浮容器并复用标准NavigationBarItem最有利于复用官方上图下文及字重；但该Item内部固定64dp高度，放大字体可能被裁剪，因此用标准Compose选择语义和最小高度Column补这一真实缺口。
- 用squircleClip包装矩形textureBlur可复用公开修饰器，但增加离屏遮罩，而且普通圆角阴影与平滑裁剪可能不同边。本项只适配官方Path到Shape，四个已有消费者共用，不造新轮廓。
- 维持官方默认底部36dp/26dp＋系统栏可以少处理安全区，但违背方案8dp目标；不通过负padding/布局偏移抵消，采用单一安全区容器。

## Related decisions

[材质选择默认液态玻璃](2026-10-20-material-selection.md)补齐6.1配置，布局决定保留；[6.3共享玻璃](2026-10-20-melox-glass.md)已替代原固定模糊并接入Melox图文折射、玻璃胶囊/交互与双端倾斜传感器；[6.4无障碍](2026-10-20-glass-accessibility.md)补可见键盘焦点与系统透明效果降级。设备待验收范围见[视觉计划](../../../../docs/settings-redesign.md)。

[五入口](../../implemented/feature/2026-10-18-five-destinations.md)的顺序/图标/状态保留，仅图标专用底栏被替代；[原悬浮材质](../../implemented/bug-fix/2026-10-19-floating-chrome.md)的捕获和模糊参数保留，底部轮廓与安全区被替代；[紧凑顶部](../../implemented/feature/2026-10-19-compact-page-header.md)不改。固定源路径与行号见[资料入口](../../../../docs/references.md)。

## Consequences

新增5项Python接线守卫，累计44项通过；结构与git diff --check通过。覆盖五入口/App唯一回调、常显纵向文字与粗细、等宽/最小高度/选择语义和按压接线、单次安全区/列表内边距、官方Path与四消费者统一shape及未改模糊参数。初跑发现守卫误把订阅页既有位置参数当作遗漏，已改为明确覆盖4个具名参数和该位置参数；不为迁就测试改调用方。

本项运行时只涉及DriftNavigationBar、ChromeMaterial及NavigationBarShape，分别107/49/22行，无巨型类、新依赖、配置或冗余旧底栏分支。五项布局守卫是源码检查，不运行Compose语义树/像素或原生测试。Kotlin仍68项声明未运行，未进行Gradle/Xcode/真机验收，未提交、推送或构建。

## Risks

同一Shape消除了代码层面的两种几何，但Generic Outline在双端textureBlur/阴影中的像素呈现与性能仍待真机验证；静态通过不代表已无露角。仍需验收200%字号下的标签截断、横屏/RTL/小窗口触区、读屏及底部系统手势。固定版悬浮组件没有滑动指示器，本项只接通选中粗细/颜色与按压反馈；第7项统一处理指示器过渡和减少动态效果，不能宣称官方动画已在本项继承。第6项的6.1选择/持久化已由[材质配置](2026-10-20-material-selection.md)补齐，其余效果与第7项待实施。
