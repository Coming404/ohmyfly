# 版本与工程选型记录

## 1. Miuix 发布基线

本轮交叉核查 GitHub 正式 release、标签源码与 Klibs MCP：

| 项 | 结果 |
|---|---|
| Miuix GitHub 最新正式 release | v0.9.4，prerelease=false |
| Maven 坐标 | top.yukonga.miuix.kmp:miuix-ui:0.9.4 |
| Klibs getLatestVersion | latestVersion=0.9.4，latestStableVersion=null |
| 库的 API 稳定承诺 | README 仍明确 experimental，不能宣称 API 已稳定 |
| 0.9.4 工具链参考 | Kotlin 2.4.20、Compose Multiplatform 1.12.0、AGP 9.4.1、Gradle 9.7.1 |
| 上游构建 JDK | 21；作为 Actions 候选基线，与本地 17 无关 |
| 上游 Android 核心 minSdk | 24 |
| 独立 miuix-blur minSdk | 33；用户现已明确最低 Android13/API33，接入同版0.9.4 |

因此“使用稳定版”在本项目中的落地是：**使用官方正式发布、非预发布版本，当前候选 0.9.4；不使用 snapshot/main 分支，不把 0.x 正式发布误称为 API 稳定保证。**若用户要求全依赖树无任何 alpha/rc，需额外审查传递依赖，不能仅看顶层版本号。

以上版本已经写入当前工程的直接声明：Kotlin2.4.20、Compose Multiplatform1.12.0、Miuix0.9.4、AGP9.4.1、Gradle Wrapper9.7.1。**基线9930858已通过双端Release；本轮材质与运行时清理尚未云端编译，未生成完整传递依赖锁或校验元数据**。Compose Compiler与Kotlin插件同版本。禁止`+`、`latest.release`或自动跟随main；升级需要兼容性验证。Version Catalog并不自动锁死整个传递依赖图。

`mavenCentral()` 是依赖仓库地址，不是另一种 Compose 版本，也没有需要指定的“仓库版本”。Android 的 Compose/AndroidX 依赖通常还需 `google()`；插件仓库按实际插件使用配置。Klibs 本轮查到 Compose UI 最新正式版为1.12.2，但不自动取代对齐 Miuix 的1.12.0候选。

### iOS 最低版本的证据边界

- 当前官方兼容页针对 Compose Multiplatform **1.12.2**列出 iOS **14** 为框架下限；这不是本项目1.12.0 + Miuix + Go完整组合的验收证据。
- Miuix 0.9.4未在已查文档中明确给出独立的统一 iOS最低承诺；其示例工程17.6/26.0目标也不等于库下限。
- 完整App下限取共享框架、Miuix及传递原生依赖、Go库、宿主实际API使用中的最高要求，目前尚未完成链接/可用性验证。
- 本项目产品目标仍是 **iOS16.0**。不得把“目标16”写成“整个组合已经验证支持16”，也不因示例高版本而自动提高到17。
- Android API33对应Android13；用户已明确将最低版本提升到33，并接入 miuix-blur。API31/Android12不再是当前支持下限。

## 2. 项目脚手架

本次检查Miuix官方组织仓库与入门文档，未找到独立官方Starter/Template仓库；官方`example`是包含多平台的组件演示工程，并非精简App模板。`Scaffold`只指页面布局容器，不指工程生成器。项目现保留官方KMP模板的双端基础文件，并单独配置Miuix。资料入口见 [references.md](references.md)。

### 首选：Kotlin/KMP-App-Template

https://github.com/Kotlin/KMP-App-Template

官方共享 UI 模板，未归档，本轮查到 Android/iOS 构建工作流、shared模块、Wrapper、版本目录。最符合手机编辑、GitHub Actions构建。需裁剪桌面/Web与示例业务，原工作流的 Xcode路径/版本不可假设新 runner永远存在。

这是带示例的模板，不是安装后立即满足本项目 Go桥接/Miuix/iOS16的成品。

### 备选：官方 KMP Wizard

https://kmp.jetbrains.com/

选择 Android+iOS 与共享 Compose UI，适合从最小工程开始。生成物仍需接 Miuix、Go和Actions。IDE向导属于官方创建项目方式，但不要求在当前手机安装IDE。

### 不优先

- JetBrains/compose-multiplatform-template：仓库已归档，不用它当作最新基线。
- Miuix example：用来学组件，不把多平台演示工程当成生产脚手架。
- 大而全社区模板：容易带入无需求的账号、数据库、DI和导航依赖；除非证明减少实际工作，否则不优先。

## 3. 产品名称：OhMyFly

产品名已确定，取漂流和轻盈滑行的意象。Android applicationId 与 iOS bundle identifier 已为 `com.ohmyfly.app`；签名团队、图标与中文名仍待定，商标/商店重名/域名未查。文档以当前工程`docs/`为唯一维护位置。

## 4. UI 依赖与材质

- UI沿用Miuix0.9.4全部正式模块与完整图标，8模块均显式声明。miuix-nav不自动改写现有五个目的地的路由，不新增平行Material页面。底栏/控件签名按固定tag核对；具体行为见[UI实现](ui-showcase.md)。
- 当前已移除全局Crossfade与展开体验，删除不再直接使用的Compose animation目录别名和声明；Miuix自身需要的传递动画依赖不因此被排除。不引入第三方动效库。

### miuix-blur 0.9.4 接入核验

- Klibs MCP `searchProjects("miuix")` / `getLatestVersion(top.yukonga.miuix.kmp:miuix-blur)` 返回0.9.4，`latestStableVersion=null`仍只表示索引未报告稳定版；项目级目标包含 AndroidJvm、ios_arm64、ios_simulator_arm64。
- 逐制品交叉检查 [Gradle Module Metadata](https://repo.maven.apache.org/maven2/top/yukonga/miuix/kmp/miuix-blur/0.9.4/miuix-blur-0.9.4.module)，确认 android、iosarm64、iossimulatorarm64 真实变体，而不是只凭项目级KMP标记。固定同版版本，不额外直接依赖其 api 传递的 miuix-shader。
- 上游 [build.gradle.kts](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-blur/build.gradle.kts) 和 Android AAR manifest 均为minSdk33，因此本项目Android App与shared统一提升到33。模块沿用上游Apache-2.0许可，不表示整个App许可已定。
- 最小接入为 `rememberLayerBackdrop` + `Modifier.layerBackdrop` + `Modifier.textureBlur`；[blur指南](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/docs/guide/blur.md)与[顶栏示例](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/example/shared/src/commonMain/kotlin/utils/PageUtils.kt)均按固定tag核实。没有臆造 BlurState/blurSource API，不加其他模糊库。
- iOS走Skiko/SkSL，源码未见高于16的独立系统API要求；不把上游示例17.6/26.0部署值当作库下限。保持产品目标16.0，最低系统实际性能和效果待真机验证。

## 5. 来源与验证范围

- Miuix release：https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4
- 版本目录：https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/gradle/libs.versions.toml
- 构建设置：https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/build-plugins/src/main/kotlin/BuildConfig.kt
- Scaffold：https://compose-miuix-ui.github.io/miuix/components/scaffold.html
- 固定版 Scaffold：https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/docs/components/scaffold.md
- Compose兼容：https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- 核心参考：faceair/clash-speedtest（1e1fb9e）、sinspired/subs-check-pro（e53554f）。

KMP依赖需先查Klibs，再核对上游许可证与最低系统。所有可查询入口集中在[资料索引](references.md)，不依赖本地调研克隆目录。

已完成：共享五页、双端宿主、Miuix全模块、固定直接依赖、Wrapper与本地检查。9930858已通过双端Release和8项共享测试；本轮悬浮层与生产源码清理尚未编译。Go桥接、塔台存储、最低系统真机性能与最终发布许可仍未完成。

## 6. Miuix 0.9.4 全模块与完整图标

用户明确要求全部正式模块，不使用基础子集。核验先查配置好的klibs MCP，后查固定tag与Maven POM/module元数据；实现决定见[全模块接入](../.agents/notes/implemented/feature/2026-10-18-miuix-full-modules.md)。

| 正式artifact（group均为top.yukonga.miuix.kmp） | 用途 |
|---|---|
| miuix-ui | 基础UI，含Basic图标 |
| miuix-core | 共享工具与MiuixIcons根对象 |
| miuix-preference | 设置偏好行组件 |
| miuix-icons | 完整扩展图标与Light/Normal/Regular/Medium/Demibold权重 |
| miuix-blur | 模糊 |
| miuix-shader | shader底层 |
| miuix-squircle | 平滑圆角 |
| miuix-nav | 本版独立导航运行时 |

- 8个模块均显式声明、统一version.ref=miuix=0.9.4。core/squircle/shader本来可由ui/blur传递获得；显式列全是为审计用户的全模块要求，不是Gradle会加载多份同版依赖。
- Klibs项目命中compose-miuix-ui/miuix；逐artifact getLatestVersion均0.9.4，latestStableVersion均null，工具链Kotlin2.4.20/Gradle9.7.1。项目目标含AndroidJvm、ios_arm64、ios_simulator_arm64；逐artifact Maven module变体亦已核对。MCP不提供完整最低系统、许可证、依赖图和模块删除状态，不能仅凭索引决定。
- 固定tag的[settings](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/settings.gradle.kts)与[发布说明](https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4)确认miuix-nav取代miuix-navigation3-ui。旧navigation3-ui只到0.9.3，不混入0.9.4；shapes等历史模块也不属于本版。
- ui/icons以api传递core，ui传递squircle，blur/squircle传递shader；preference传递ui。引入nav不等于已迁移路由，现有五目的地状态仍保留。
- [图标文档](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/docs/zh_CN/guide/icons.md)及对应源码核对完整目录：Basic的7个图标由ui提供；icons提供156个扩展图标及五档权重。不存在独立miuix-icons-basic或Useful分组。导航采用Home/Link/Stopwatch/Layers/Settings；原通用自绘路径已删除，Wave品牌保留。
- Android库通用min24，blur独立min33，项目API33满足声明要求；库未给独立iOS部署下限，保持产品目标16，不把示例或框架最低版本当作组合真机证据。POM许可证Apache-2.0不决定未来Go组合程序的最终许可。
- 已知传递预发布：ui的material3-window-size-class:1.12.0-alpha03。不能声称整个图无alpha/rc；不为该提示擅自替换依赖。8模块已在9930858双端Release解析编译通过；本轮材质/清理未编译，未生成全图锁或校验元数据，最低系统真机性能仍待验。
