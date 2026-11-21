# UI 实现与验收

Android与iOS共用 `shared/src/commonMain` 的Miuix界面，保留概览、塔台、起飞、航班、设置五个入口。本轮源码迁移到Miuix0.9.5-beta03，使用液态玻璃／Expressive两档风格与Glass图标。本轮新增材质G2补丁及升级已通过Android编译、415项共享测试与iOS Release归档，真机shader渲染和帧时间待验收；记录见[beta03验证](miuix-beta03-verification.md)。

## 页面与状态

| 页面 | 当前内容 |
|---|---|
| 概览 | 目录数量、已选数量、最近结果空状态、四个功能入口 |
| 塔台 | 正式空状态；不读取文件或请求网络 |
| 起飞 | 选中航班范围、待起飞列表；引擎接通前开始按钮禁用 |
| 航班 | 航班摘要、地区分类、多选、当前分类全选及全局清空；没有航班时显示空状态 |
| 设置 | 分组子页、真实设置持久化、GitHub镜像管理与独立HTTP探测；[详细边界](settings.md) |

一级选择与五个滚动位置由App持有，当前一级入口按稳定名称保存。设置子页使用App唯一可保存NavBackStack，SettingsScreen的NavDisplay实际承载页面；不把一级底栏点击入栈，也不复制AppSettings。航班摘要通过参数下传，未知延迟为空并显示“未起飞”，不得用固定数值填充。选择夹具只存在于commonTest，不参与应用运行时。

## 悬浮控制层与材质

- 顶栏、标题胶囊和搜索/筛选工具栏复用 G2 `NavigationBarShape` 与 Miuix `glassPanel`，液态玻璃档开启折射/rim 光照。
- 顶部背景使用 Miuix `progressiveTextureBlur`，渐变带与顶栏实际测量高度一致，使用 `ProgressiveBlur.Top`；模糊在标题与按钮胶囊底边附近衰减至零，不延伸到正文。
- 塔台页及航班选择态的顶部/底部工具栏共用同一 G2 玻璃表面。选择态“取消/全选”采用 `onSurface`，浅色为黑、深色为亮色。
- 航班分组的新增按钮保留44dp触控范围，图标缩至18dp并取消静态底色。
- 主导航为应用自有五项 G2 胶囊，使用 Miuix `glassPanel`、`EdgeStroke` 与折射/rim 光照。材质保留 Miuix 模糊和主题混色，将第二、第三颜色层 alpha 调至0.34与0.18以提高背景透出；玻璃不可用时使用实色表面。
- 五项等宽、高度54dp、内容内缩8dp。选中项使用主题主色、Glass Medium图标与SemiBold标签；拖动时胶囊连续跟手，松手提交目标并弹簧归位。外层安全区由应用一次处理。
- 五页列表保持全高视口，Scaffold边距使用列表contentPadding，背景仍能滚入悬浮控件后方。
- 页面内容录制一个 Miuix LayerBackdrop。导航、工具栏、菜单和弹层复用背景源，各自使用官方材质参数；弹层不录入自己的背景源。
- 界面风格仅有「液态玻璃／Expressive」。前者使用官方 Miuix 玻璃材质；后者使用不透明Miuix表面，不启用玻璃采样。旧Kyant Backdrop、四档预设及旧版透镜高光引擎已移除；底栏拖拽由应用导航组件负责。
- AppTheme 根显式启用 `LocalSquircleEnabled`，Miuix Card、IconButton 等内置圆角组件默认绘制 G2；设置页及二级页 `SettingsCard` 使用24dp G2表面和同轮廓描边。自绘表面统一用 `AppCorners.shape`/`continuousCorners`，真圆和胶囊保留解析圆弧。
- 玻璃菜单、对话框、抽屉由 ui-material 的 Diy_ 补丁让折射、遮罩、高光和阴影与实际 G2 路径一致。
- 双端传感器与生命周期继续复用，驱动官方材质光照方向。降低透明度时走实色，降低动态效果时停止应用自有运动和重力光照。
- UI动作图标统一 Glass Regular。应用品牌、GitHub/媒体平台/地区旗帜与塔台专属业务标识独立保留。

## 配色

- 液态玻璃档：Android自动项使用系统动态代表色＋Neutral/Spec2025；iOS自动项使用Miuix默认浅深色。用户选预设时两端均使用所选种子。
- Expressive档：两端使用ThemePaletteStyle.Expressive；Android自动项使用系统动态种子，iOS自动项使用产品蓝。手选的7个种子在两种风格下都由当前 Monet 算法生成明暗语义色。
- 配色独立于界面风格并持久化为 `AccentPalette`。Android“自动”使用当前系统壁纸 Monet 种子；iOS“自动”在 Expressive 模式使用产品蓝 `#1F6FE5`。可手选绯红、紫罗兰、海洋、鼠尾草、琥珀、玫瑰或宫崎骏森林绿种子；手选预设优先于系统动态种子，Miuix 按当前界面风格及明暗生成整套语义色。系统壁纸仅用于 Android 自动项，不读取 iOS 壁纸。
- 明暗、字体缩放、主题色过渡继续使用已有设置及单一ThemeController；不引入第二套Material3组件。

## 页面与设置卡片层级

全局页面底色与LayerBackdrop捕获的不透明底色保持同值，不修改Miuix原始配色或动态颜色标记。当前表面映射：

| 场景 | 页面 | 设置卡片 / 前景 |
| --- | --- | --- |
| iOS内置浅色 | #F0F1F3（原#F7F7F7） | surfaceContainer / onSurfaceContainer，默认白底黑字 |
| Android动态浅色 | Neutral的surfaceContainerHigh | surface / onSurface |
| 深色（两端） | surface | surfaceContainer / onSurfaceContainer，保持原映射 |

设置首页的外观组及全部子页（含镜像编辑、线路和删除确认）复用SettingsCard，继续使用Miuix Card和CardDefaults。普通设置组不加阴影；使用官方squircleBorder绘制0.5dp平滑轮廓，浅色黑4%/深色白8%，应用卡片圆角统一24dp。仅以根层派生的四个只读颜色统一页面和设置表面。其他一级页目前只跟随共享页面底色，卡片内容与布局尚未改。

## 设置首页与预设选择弹层

首页包含40dp Wave标记、OhMyFly、单行产品简介及实际平台版本；明暗模式、界面风格与减少透明效果直接在外观组修改。六个复杂设置继续进入子页，旧外观子页已移除。

没有下级设置、只从已有值中选择的主题/材质/起飞默认模式，复用现有设置行与官方OverlayCascadingListPopup：当前值＋常驻上下双箭头，点击打开邻近的选择弹层，当前项对勾，单选即关闭。弹层状态、菜单语义、触感、遮罩与进出场直接交由官方组件，复用App根Scaffold，不新增Window、嵌套宿主、额外BackHandler或自定义动画。旧原地展开区、两处页面展开状态及旋转箭头分支已删除。

跳转行仍由BasicComponent承载16sp标题/14sp当前值，开关同样16/14sp，普通右箭头（RTL镜像）仅表示子页。预设选择行恢复官方headline1/body2字号、默认16dp内边距与BasicComponent 56dp下限，不为16sp覆盖再嵌套主题。设置列表左右和相邻项间距仍16dp。ChoiceOption的官方RadioButton保留给航班分类、镜像编辑等原调用方，不用于重新绘制官方菜单。

6.4的源码检查补齐了iOS系统减少透明效果输入、最后绘制的键盘焦点轮廓，以及设置字段error/liveRegion语义和操作行FlowRow。没有改写用户材质选择，也没有新增设置摘要；[设备验收矩阵](#64-无障碍与降级设备矩阵)仍待执行。第7项另补应用减少动态偏好与主题/材质180ms单树过渡，范围及限制见下节。

## 第7项：运动策略与短过渡

首页提供独立的“减少动态效果”。应用偏好或Compose系统MotionDurationScale=0任一限制生效时，自有主题/材质过渡立即取终值，选中胶囊松手后即时定位、不执行弹簧回弹；直接拖拽仍随手指移动。页面导航使用NavDisplayEffects.None并关闭空间滑退，保留标题/系统返回与镜像编辑优先级。正数系统倍率沿用平台，不重置为1；减少透明效果仍单独负责实体降级。

正常模式下主题53个语义色和页面/卡片4个角色继续使用应用180ms过渡。弹层及普通玻璃控件使用Miuix动效；主导航的选中胶囊由应用组件沿手势跟随、松手弹簧归位。旧四档参数动画已删除；一级页面保留既有1.35倍转场时长和唯一导航状态。

ThemeController根仍负责生成与动态标记；仅在其内使用官方colors重载覆写插值颜色，保证继承动态模式，LocalContentColor随动画色变化。这里只有一个控制器和一棵UI，不复制采样树；只用colors重载会丢动态标记，故不采用。

**应用开关不等于全局零动画。** Miuix内置Switch/Dropdown等组件仍只受系统运动限制控制；当前公共双端宿主没有轻量上下文替换接口，未引入internal API或自定义宿主。库内部导航深度驱动也不承诺当帧中止；None只移除可见空间变换，返回完成/entry清理仍需设备检查。源码交付与轻量结果见[决定](../.agents/notes/implemented/feature/2026-10-20-reduced-motion.md)。

## 页面转场与返回

正常运动下设置页面使用既有AppNavTransition共享弹簧；有效减少动态时改None及NavDisplayEffects.None。NavDisplay默认关闭滑退，本项目正常模式按布局方向显式开启LeftToRight/RightToLeft；根页不能滑退，镜像编辑打开或减少动态生效时None。PredictiveBackHandler与官方Overlay共享返回分发，优先关闭弹层/编辑，不额外装Android BackHandler或iOS空实现。页面使用实体共享底色，NavDisplay在原有760dp内容宿主内，enableCornerClip=false（不套物理屏幕圆角）、blockInputDuringTransition=true，dimAmount沿用官方0.5。设置转场沿用应用1.35倍时长；菜单手势交由官方组件，底栏拖拽由应用导航组件处理。

顶部和底栏继续位于根Scaffold，随逻辑栈更新，底栏选中胶囊不跟随页面滑退进度逐帧联动。底栏G2折射与EdgeStroke由独立玻璃表面负责，真机边界/帧时间仍待验收。重点设备验收：取消滑退保留页面和草稿；连续点开/返回不空栈；系统恢复设置路由及入口；镜像HTTP任务离页后在entry清理时取消；弹层先消费返回，RTL方向与最低系统一致。

## 性能与源码职责

五个一级页面仍只绘制当前目的地，不增加全局双页Crossfade；设置NavDisplay在页面转场期间会同时呈现相关entry，转场结束后按库生命周期清理，不再声称所有时刻只有单页。没有自定义滚动逐帧重组或第二套页面动画；Miuix自身转场遵循上游实现，不盲目关闭ProMotion或限制系统字体缩放。

- `App.kt`：单一采样源与页面切换；`ui/AppTheme.kt`集中原有主题控制器和唯一MiuixTheme根，`ui/AppSurfaceColors.kt`只派生页面/设置卡片/卡片前景/轮廓，不持有另一份主题状态。`PersistedSettings.kt`统一加载与保存错误处理，`settings/`持有模型和DataStore；`mirror/`负责有界公开资源探测。
- `ui/{DriftTopBar,DriftNavigationBar,ChromeMaterial}.kt`：悬浮控制层、共享材质和五项导航行为；`ui/NavigationBarShape.kt`集中导航玻璃与指示胶囊的G2轮廓。
- `ui/PagePadding.kt`：逻辑方向安全区和内容留白合并。
- `ui/{NodeSummary,NodeSelection,NodeSelectionActions,ChoiceOption}.kt`：航班摘要、选择逻辑和控件。
- `ui/{OverviewScreen,SubscriptionsScreen,SpeedScreen,NodesScreen,SettingsScreen}.kt`：五页。
- `commonTest`：NodeSelectionTest、PagePaddingTest、AppDestinationTest、ThemeModeTest等，SettingsNavigationTest六项覆盖开页/防重/根保护/序列化；MaterialSettingsTest新增六项覆盖材质默认/往返/旧值迁移/损坏回退/透明度偏好保留，SettingsCodecTest补非默认材质往返。当前146项已在78d80bd的Android host任务执行且全部通过，无失败或跳过；真实测试保留标准命名。

## 验收清单

本地结构与Python测试不是Kotlin编译器；完整构建按[开发流程](development.md)交给Actions。当前修订未做真机帧耗测量，不报告性能改善百分比。

1. 五页切换时只显示一个18sp紧凑页名，子页返回为纯图标；无重复品牌、内容大标题或空操作胶囊。首末项可见，滚动正文不干扰标题，底栏仍没有全宽白底。
2. 浅/深色及减少透明效果下，顶底参数与回退保持一致，无黑白闪烁或残留旧主题采样。
3. 横屏、刘海/手势区、RTL与大字体下按钮仍可触达，页名必要时省略但读屏文本完整；标题和返回按钮不依赖固定高度裁剪。
4. 空目录不显示假延迟；航班后端接通前不能启动航班起飞。镜像探测独立实现且只在用户主动点击时联网，不把镜像结果当航班结果。
5. VoiceOver/TalkBack正确播报目的地、选择状态和操作名称，装饰图标不重复播报。
6. 最低iOS16/Android13真机检查；用Instruments/帧时间对比滚动与切页，不把静态审查当成卡顿根因或GPU性能证据。

### 6.4 无障碍与降级设备矩阵

以下全部待设备执行，源码检查不能将状态改为通过。需先由后续授权的双端构建生成可运行版本。

| 条件 | 操作与预期 |
|---|---|
| 系统/应用减少透明效果 | 四材质逐一切换两个开关：任一为true，两栏实体、无残留采样；两者恢复后显示原材质，重启不改写应用偏好。iOS后台改系统设置后回前台也刷新 |
| RuntimeShader不可用 | 用调试环境提供不可用能力输入：两栏实体，顶栏返回和五入口仍工作；无透明空壳、无图文录制或传感器使用 |
| VoiceOver/TalkBack | 按序遍历五个Tab，只播报各项完整名称/选中状态一次；返回名称正确，隐藏图文和焦点轮廓不进入遍历；选择菜单打开/关闭后焦点返回触发项 |
| 硬件键盘 | Tab/方向导航进入所有Tab，轮廓在选中胶囊上仍可见；Enter/Space仅激活一次；焦点不等于导航提交，退出底栏轮廓消失，RTL方向正确 |
| 200%字体/窄屏/旋转 | 标签可有意省略但读屏完整，主体自然增高；顶栏按钮仍48dp触区；编辑与恢复确认按钮换行且不挤出屏幕，IME不遮输入，安全区只应用一次 |
| 输入校验 | 输入越界/无效值，字段播报错误且可见消息适时通知；保存禁用，纠正后恢复；取消和默认恢复行为不变 |
| 明暗/复杂背景 | 亮暗图文、连续滚动及色块背景下检查底栏标签/边缘和键盘轮廓对比、胶囊内字形采样，不能仅用主题色数值判通过 |
| 无传感器/采集错误/后台 | 静态光源仍有基础玻璃能力；无硬件不重复重试，前后台恢复不累积manager/listener；实体模式停止采集 |
| 减少动态与性能 | 应用/系统分别切换，确认项目自有主题/材质取终值、胶囊形变/追光/回弹停止；官方组件按系统设置验收，不把应用开关当全局禁用。最低系统实测滚动、拖动与切页帧耗，不以静态通过声称性能改善 |

### 第7项运动设备矩阵

以下均待设备执行：

| 条件 | 操作与预期 |
|---|---|
| 运行中过渡→减少动态 | 主题/材质180ms过渡或胶囊拖动中开启应用开关、切换系统限制：自有视觉直接终值，无残留缩放/光斑，重新开启运动不回放旧橡皮筋；未提交拖动不改已选入口 |
| 系统倍率与前后台 | Android系统0/0.5/1/2倍、iOS减少动态开关及后台改动后回前台：更严格的一方生效，正数倍率不被重置，应用关闭不覆盖系统禁止 |
| 官方组件边界 | 应用开关单独开启时Switch/Dropdown仍可能有动画；系统禁止后观察官方组件收敛。不能将局部门控计为全组件覆盖 |
| 反向/快速切换 | 连续明暗、动态色刷新、四材质切换并模拟保存失败：从当前显示值平滑重定向，失败恢复已确认值，无双层玻璃或旧主题闪白 |
| 返回/草稿/焦点 | 普通与减少动态两种模式各测标题/系统返回、编辑取消和RTL；手势中途切换限制，页面/草稿不丢，entry正常清理，读屏与键盘选中不重复 |
| 颜色与性能 | 180ms中间帧对比度、页色/采样色同步及53角色最终颜色，浅深色截图；最低系统记录帧耗，不以源码行数证明性能 |
