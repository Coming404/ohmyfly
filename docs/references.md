# 开发资料入口

这里保存可重复查询的上游入口；开发规则在 [AGENTS.md](../AGENTS.md)。入口存在不等于已验证所有API，版本敏感问题必须切换到本项目锁定的release/tag。

## 查询顺序

1. 查看 `gradle/libs.versions.toml`、Wrapper及相关决定，明确当前版本和平台。
2. **本项目的 Miuix 问题优先使用已安装的 `miuix` Skill**：`/var/minis/skills/miuix/SKILL.md`，仅按需加载其引用；已有充分证据直接复用，不重复读取或默认联网。
3. **降级为 EXA MCP 查询**：仅在 Skill 不可用、资料缺失、版本/源码冲突，或精确 API/行为仍缺证据时补查。优先查官方且与项目锁定版本一致的资料；EXA 无法取得必要证据时才使用浏览器，不直接以 curl/wget 绕过顺序。
4. 版本敏感参数仍以匹配版本的源码为准；Skill 优先不代表跳过核实或编译/真机验收。本页的官方链接是补充入口，不再触发“每次先联网”。
5. 新增或推荐KMP依赖前，仍先查已配置的Klibs MCP，再读官方仓库/许可证/发布记录；非Miuix的工具链兼容问题查Kotlin/Gradle/Android官方资料。
6. 结论注明来源、版本、未验证项。发现过期链接或更换依赖时同步更新本页。

## Klibs 与依赖仓库

- [Klibs检索](https://klibs.io/)：查多平台库、作者、目标平台与坐标。
- [Klibs MCP](https://api.klibs.io/mcp)：使用已配置的`klibs`服务器。当前工具为`searchProjects`、`getLatestVersion`，调用前以实际schema为准。
- [Maven Central](https://central.sonatype.com/)：查制品、POM、版本与元数据。
- [Google Maven](https://maven.google.com/)：AndroidX、Android构建相关制品。
- [Gradle Plugin Portal](https://plugins.gradle.org/)：构建插件及说明。

MCP版本/目标信息不足以证明许可证或持续维护；缺失字段查官方LICENSE、commits、releases、issues。注意同名fork和`latestStableVersion=null`的语义。

## 设置持久化与镜像

- [设置实现与边界](settings.md)：DataStore版本、沙盒路径、依赖核对与镜像选择／降级契约。
- [DataStore多平台文档](https://developer.android.com/kotlin/multiplatform/datastore)；[1.2.1发布](https://developer.android.com/jetpack/androidx/releases/datastore#1.2.1)。
- [Miuix 0.9.4 TextField](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/TextField.kt)：新表单优先state重载，不套用旧教程输入签名。

## Miuix

- [官方文档](https://compose-miuix-ui.github.io/miuix/)；[中文文档](https://compose-miuix-ui.github.io/miuix/zh_CN/)。
- [官方中文快速开始](https://compose-miuix-ui.github.io/miuix/zh_CN/guide/getting-started)；[英文入门](https://compose-miuix-ui.github.io/miuix/guide/getting-started.html)。
- [Scaffold](https://compose-miuix-ui.github.io/miuix/components/scaffold.html)：页面容器、insets和弹层宿主，不是工程模板。
- [主题](https://compose-miuix-ui.github.io/miuix/guide/theme.html)；[文字样式](https://compose-miuix-ui.github.io/miuix/guide/textstyles.html)。
- [0.9.4 ThemeController](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/ThemeController.kt)：Android以MonetSystem＋系统动态代表色作为非空种子生成Neutral/Spec2025；iOS使用System与默认浅深色。keyColor=null的分支不使用自定义style/spec，不能混同。
- [0.9.4 Android动态色实现](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-ui/src/androidMain/kotlin/top/yukonga/miuix/kmp/theme/DynamicColors.android.kt)：上游也使用system_accent1_500作为种子读取失败时的替代；本项目只依赖公开系统色资源，不读取隐藏设置键。
- [Android system_accent1_500](https://developer.android.com/reference/android/R.color#system_accent1_500)：API31起的系统强调色色阶，不等同壁纸原始种子；[Activity资源配置变化](https://developer.android.com/guide/topics/resources/runtime-changes)。
- [组件源码](https://github.com/compose-miuix-ui/miuix/tree/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp)。
- [0.9.4完整文档源码](https://github.com/compose-miuix-ui/miuix/tree/v0.9.4/docs)：网站可能比锁定版本更新，标签内文档也可能滞后；以同版本组件源码或发布 sources.jar 为最终签名依据。
- [0.9.4 基础行 Component](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Component.kt)、[SmallTitle](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/SmallTitle.kt)、[Card 示例](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/example/shared/src/commonMain/kotlin/component/CardSection.kt)：概览页默认排版依据。
- [0.9.4 blur 指南](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/docs/guide/blur.md)、[顶栏模糊用法](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/example/shared/src/commonMain/kotlin/utils/PageUtils.kt)：核对真实的 layerBackdrop / textureBlur，而非套用其他库API。
- [0.9.4 NavigationBar 源码](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/NavigationBar.kt)：标准NavigationBarItem为RowScope扩展、上图下文，固定64dp/26dp图标/12sp标签；FloatingNavigationBarItem只有28dp图标，label仅是读屏名称，没有文本槽或滑动指示器。悬浮容器content无RowScope，iOS36dp/Android26dp＋系统栏的底部留白不可取消。第5项因此用应用局部Box/Row/Column与Compose标准选择语义替换布局，而非伪造alwaysShowLabel参数；不迁移App路由状态。
- [0.9.4公开平滑路径](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-squircle/src/commonMain/kotlin/top/yukonga/miuix/kmp/squircle/SquirclePath.kt#L49)与[平滑轮廓指南](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/docs/guide/squircle.md#L197)：Path.addSquircleRect(width,height,cornerRadius,extension=1.1f,squircleEnabled=true)可适配Compose Shape/Outline；无公开SquircleShape。底栏NavigationBarShape只做该桥接，24dp与默认extension传给原ChromeMaterial四处消费，不自写几何/Shader或叠加离屏遮罩。证据先Skill，缺签名部分经EXA固定commit补齐，原生渲染仍未验收。
- [0.9.4基础行内容槽](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Component.kt)与[ArrowPreference](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-preference/src/commonMain/kotlin/top/yukonga/miuix/kmp/preference/ArrowPreference.kt)：第3项先用Skill组件/偏好指南，再以EXA MCP补齐精确签名。BasicComponent内置56dp最小高度、16dp默认内边距，内容槽支持onClickLabel/role，应用仅定制16sp/14sp文字和12dp垂直内边距；跳转沿用官方ArrowRight图标和RTL镜像。ArrowPreference字符串标题不支持内容槽，不为52dp重画基础行。
- [0.9.4 OverlayDropdownPreference](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-preference/src/commonMain/kotlin/top/yukonga/miuix/kmp/preference/OverlayDropdownPreference.kt)和[Dropdown基础实现](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Dropdown.kt)：用户截图纠正后，主题/测速预设改为官方弹层；items/selectedIndex/onSelectedIndexChange由薄适配映射稳定ID。官方上下双箭头是ArrowUpDown，默认10x16dp；当前值用body2、标题用BasicComponent的headline1，默认16dp内边距与56dp行高下限；单选后自动关闭。直接复用根Scaffold及上游弹层/动画，不自画图标、不调用DropdownImpl等内部构件。
- [0.9.4基础Checkbox](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Checkbox.kt)与[Switch](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Switch.kt)：当前选择控件使用基础实现与整行语义，不导入已移除的extra.Super*；preference已按全模块要求引入，不等于每行都已迁移。
- [0.9.4平滑描边](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-squircle/src/commonMain/kotlin/top/yukonga/miuix/kmp/squircle/SquircleBorder.kt)：SettingsCard复用squircleBorder(width,color,cornerRadius)，不新增裁剪层或重画Card。
- [0.9.4默认颜色与工厂函数](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/Colors.kt)：lightColorScheme/darkColorScheme可自定义完整配色，System只管理明暗选择。
- [0.9.4 textureBlur真实签名](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-blur/src/commonMain/kotlin/top/yukonga/miuix/kmp/blur/TextureEffect.kt)、[LayerBackdrop](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-blur/src/commonMain/kotlin/top/yukonga/miuix/kmp/blur/LayerBackdrop.kt)：blurRadius为dp、noiseCoefficient可设0；无downsampleFactor参数，捕获源复用不等于模糊结果复用。
- [0.9.4发布模块清单](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/settings.gradle.kts)：正式模块为ui/core/preference/icons/blur/shader/squircle/nav，全部同版；example/docs/baselineprofile不属于App依赖。
- [0.9.4完整图标文档](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/docs/zh_CN/guide/icons.md)：完整miuix-icons提供扩展图标与五种权重，Basic由miuix-ui提供；不臆造icons-basic或Useful分组。
- [0.9.4导航模块](https://github.com/compose-miuix-ui/miuix/tree/v0.9.4/miuix-nav)：本版用miuix-nav替代旧navigation3-ui；依赖引入和路由接入是两项任务。本项目现在已接入设置子页，五个一级入口仍互斥切换。
- [固定版NavDisplay](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-nav/src/commonMain/kotlin/top/yukonga/miuix/kmp/nav/core/NavDisplay.kt#L964)、[NavBackStack](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-nav/src/commonMain/kotlin/top/yukonga/miuix/kmp/nav/core/NavBackStack.kt)、[NavDisplayEffects](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-nav/src/commonMain/kotlin/top/yukonga/miuix/kmp/nav/core/NavDisplayEffects.kt)、[共享预测返回](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-nav/src/commonMain/kotlin/top/yukonga/miuix/kmp/nav/gesture/PredictiveBackHandler.kt)：Skill路由指引为主，EXA补核签名。entry按具体运行时类型注册，枚举每值仍是不同contentKey；官方默认MiuixDefault、手势None、dimAmount0.5、屏幕圆角半径0dp、过渡输入阻断false，本项目显式启用RTL对应滑退、编辑禁用、enableCornerClip=false与输入阻断true；沿用官方Saver，不手写栈持久化。
- [Kotlin序列化1.11.0](https://github.com/Kotlin/kotlinx.serialization/tree/v1.11.0)、[Android/多平台支持](https://github.com/Kotlin/kotlinx.serialization/blob/v1.11.0/README.md)、[Apache-2.0](https://github.com/Kotlin/kotlinx.serialization/blob/v1.11.0/LICENSE.txt)：用于可保存路由，Klibs已确认Kotlin官方/JVM1.8与双iOS目标、稳定1.11.0，预览1.12.0-RC未用；与锁定Miuix上游serialization-json1.11.0及Kotlin插件2.4.20对齐，未改变DataStore编码。
- [0.9.4正式发布](https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4)；[所有发布](https://github.com/compose-miuix-ui/miuix/releases)。
- [0.9.4版本目录](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/gradle/libs.versions.toml)。
- [构建设置](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/build-plugins/src/main/kotlin/BuildConfig.kt)；[模糊模块](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-blur/build.gradle.kts)：查最低SDK。
- [官方example](https://github.com/compose-miuix-ui/miuix/tree/v0.9.4/example)：组件演示与宿主参考，不是独立精简Starter。
- [官方组织仓库](https://github.com/orgs/compose-miuix-ui/repositories)：本次未找到独立官方应用模板仓库；以后可能变化，应再次核对。
- [Miuix许可证](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/LICENSE)。

## Melox 液态玻璃移植基线（6.3源码已接入）

- [固定版底栏](https://github.com/Inefy-03/Melox/blob/49ee06aa1f01a67eb5ea1d5cc7307ddfd571a534/app/src/main/java/com/melox/player/ui/component/liquid/LiquidGlassNavigationBar.kt)与[Lens](https://github.com/Inefy-03/Melox/blob/49ee06aa1f01a67eb5ea1d5cc7307ddfd571a534/app/src/main/java/com/melox/player/ui/component/liquid/Lens.kt)：6.3照搬图文录制/CombinedBackdrop、边缘折射/色散、BloomStroke、玻璃选中胶囊与弹簧交互；Melox本身仅Android，图文字形采样结论尚待运行验证。
- [Miuix Skia着色器](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-shader/src/skikoMain/kotlin/top/yukonga/miuix/kmp/shader/RuntimeShader.skiko.kt)与[图层适配](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-blur/src/skikoMain/kotlin/top/yukonga/miuix/kmp/blur/internal/RenderEffectCompat.skiko.kt)：iOS可复用RuntimeEffect及ImageFilter.makeRuntimeShader，不因Android AGSL名称预设另写Metal或实体降级。[非Android倾斜接口](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-blur/src/skikoMain/kotlin/top/yukonga/miuix/kmp/blur/sensor/DeviceTilt.skiko.kt)当前返回Zero，后续补iOS Core Motion适配及生命周期。
- [Melox许可](https://github.com/Inefy-03/Melox/blob/49ee06aa1f01a67eb5ea1d5cc7307ddfd571a534/LICENSE)：整体GPL-3.0，部分移植文件有Apache-2.0头和Kyant0归属；逐文件核对及履行义务，不将整体实现概括为Apache。现阶段[材质选择与持久化](../.agents/notes/implemented/feature/2026-10-20-material-selection.md)、[共享玻璃效果链](../.agents/notes/implemented/feature/2026-10-20-melox-glass.md)与[双端传感器](../.agents/notes/implemented/feature/2026-10-20-glass-gravity-sensor.md)均已接入源码，原生编译与真机验收待执行。

## Kotlin / Compose / 工程

- [Kotlin Multiplatform文档](https://kotlinlang.org/docs/multiplatform.html)。
- [Compose版本与平台支持](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)。
- [KMP兼容指南](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)。
- [Compose Multiplatform仓库](https://github.com/JetBrains/compose-multiplatform)；[1.12.0发布](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.0)。
- [KMP官方共享UI模板](https://github.com/Kotlin/KMP-App-Template)：本工程Wrapper/iOS宿主来源。
- [官方创建向导](https://kmp.jetbrains.com/)：需要重新生成干净工程时参考。
- [共享资源](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources.html)。
- [Apple framework直接集成](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html)。

## 第7项运动策略与主题过渡依据

- [MotionDurationScale](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale)是协程上下文元素，不是CompositionLocal；0结束标准运动，正数倍率保留。固定Compose1.12.0提交`f29d2f99f3beafc60992c6a73dbbaa9f841c5d4c`的[SuspendAnimation](https://github.com/JetBrains/compose-multiplatform-core/blob/f29d2f99f3beafc60992c6a73dbbaa9f841c5d4c/compose/animation/animation-core/src/commonMain/kotlin/androidx/compose/animation/core/SuspendAnimation.kt)逐帧读取scaleFactor，局部rememberCoroutineScope不能替换子组件effect上下文。
- [iOS ComposeContainer](https://github.com/JetBrains/compose-multiplatform-core/blob/f29d2f99f3beafc60992c6a73dbbaa9f841c5d4c/compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/scene/ComposeContainer.ios.kt)已有UIKit减少动态联动；公开ComposeUIViewController配置没有effectContext注入口。第7项复用系统支持，应用开关只门控自有动画，不声称关闭Miuix全部内置动画。
- [固定Miuix MiuixTheme](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/MiuixTheme.kt)与[Colors](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/Colors.kt)：公开colors重载接受53角色插值，但不提供内部动态模式标记；保留controller根及只负责动画颜色的继承覆盖层。没有animateColors公共函数，不访问internal setters。
- 实际接线、拒绝自定义宿主及未执行的设备验收见[第7项决定](../.agents/notes/implemented/feature/2026-10-20-reduced-motion.md)。180ms为应用设定，不是Miuix官方默认。

## Android / iOS 系统

- [UIAccessibility 减少透明效果](https://developer.apple.com/documentation/uikit/uiaccessibility/isreducetransparencyenabled)与[状态变更通知](https://developer.apple.com/documentation/uikit/uiaccessibility/reducetransparencystatusdidchangenotification)：6.4接入系统偏好；Apple DocC 的 Objective-C 声明核实为 `UIAccessibilityIsReduceTransparencyEnabled()` 与 `UIAccessibilityReduceTransparencyStatusDidChangeNotification`，iOS8起可用，满足本项目iOS16下限。默认通知中心、主队列回调，回到前台刷新；只影响有效材质，不写回用户配置。

- [Android API版本](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels)：API31=Android12、API33=Android13。
- [Android构建工具发布](https://developer.android.com/build/releases/gradle-plugin)。
- [Android SDK工具](https://developer.android.com/tools)。
- [Apple开发文档](https://developer.apple.com/documentation/)；[Xcode支持与SDK](https://developer.apple.com/support/xcode/)。
- [Apple HIG](https://developer.apple.com/design/human-interface-guidelines/)：系统交互、布局、可访问性；不要求重画成另一套视觉库。
- [App Store审核指南](https://developer.apple.com/app-store/review/guidelines/)：发布前单独审查。

## Go 核心与上游实现

- [Go文档](https://go.dev/doc/)；[Go Mobile](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile)。
- [clash-speedtest](https://github.com/faceair/clash-speedtest)；[审查版本的测速/加载](https://github.com/faceair/clash-speedtest/blob/1e1fb9e2bbb29282cce5613a6c6b946b85169fdc/speedtester/speedtester.go)。
- [subs-check-pro](https://github.com/sinspired/subs-check-pro)；[审查版本解析目录](https://github.com/sinspired/subs-check-pro/tree/e53554f9ad2cc9770f483f0c08b7d9ea911c331d/proxy/parse)；[去重实现](https://github.com/sinspired/subs-check-pro/blob/e53554f9ad2cc9770f483f0c08b7d9ea911c331d/utils/dedup.go)。
- [Mihomo仓库](https://github.com/MetaCubeX/mihomo)；[协议配置文档](https://wiki.metacubex.one/config/proxies/)；[许可证](https://github.com/MetaCubeX/mihomo/blob/Meta/LICENSE)。
- [原始去重函数边界测试记录](evidence/dedup-results.txt)：只证明去重行为，不是完整项目编译或真实网络测试。

## 构建与持续集成

- [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)。
- [Gradle版本目录](https://docs.gradle.org/current/userguide/version_catalogs.html)；[依赖锁](https://docs.gradle.org/current/userguide/dependency_locking.html)；[依赖校验](https://docs.gradle.org/current/userguide/dependency_verification.html)。
- [Actions文档](https://docs.github.com/en/actions)；[托管runner软件镜像](https://github.com/actions/runner-images)。
- [setup-java](https://github.com/actions/setup-java)；[setup-gradle](https://github.com/gradle/actions)；[setup-go](https://github.com/actions/setup-go)。

macOS镜像和预装Xcode会变化；不能把模板中的旧路径当作永久可用。iOS模拟器构建不等于已签名IPA或最低系统真机验证。
