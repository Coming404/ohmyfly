# 版本与工程选型记录

## 1. Miuix 发布基线

用户明确要求完整升级至 **v0.9.5-beta03**，GitHub `prerelease=true`，固定提交 `3526cfe2ca0779c5a2eab99af8737a96c9a8a885`。不是浮动 main 或 snapshot。

| 项目 | 当前声明 |
| --- | --- |
| Miuix 各模块 | 0.9.5-beta03 |
| Kotlin / Compose Compiler | 2.4.21 |
| Compose Multiplatform | 1.13.0-rc01 |
| AGP / Gradle | 9.5.2 / 9.9.2 |
| kotlinx.serialization | 1.13.0 |
| Android / iOS 最低目标 | API33 / iOS16.0 |

Klibs 查询确认 compose-miuix-ui/miuix 的 Android、ios_arm64、ios_simulator_arm64，但索引仅更新到 beta01；beta03 以 Maven 发布元数据和固定源码交叉核对，ui/blur/material/glass/glass-icons/nav 均有对应平台变体。删除旧 miuix-shader 与 Kyant Backdrop 依赖，保留独立 Shapes1.2.1 几何。

界面只有液态玻璃和 Expressive 两档，均使用 Miuix 控件；AccentPalette 的手选种子只在 Expressive 模式显示和生效，Android自动跟随系统动态代表色、iOS Expressive自动回退产品蓝，7个预设包含六种参考色和宫崎骏森林绿。液态玻璃保留Neutral/自动系统色并忽略已保存的手选种子。Monet 色板由 Miuix ThemeController 生成，详见 ui-showcase/settings。官方圆角未满足现有 G2 契约，采用 ui-shapes 与 ui-material 本地模块替换，详细接口与补丁见各模块 README。

该组合已通过Android编译、415项共享测试及iOS Klib链接/Release归档，记录见[beta03验证](miuix-beta03-verification.md)。Android compileSdk对齐37.2，iOS16/API33产品最低目标保持不变，运行时shader与最低系统真机表现待验收。

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

- 主导航和工具栏由应用自有五项 G2 capsule / Clear surface 实现，统一到 Glasmio Clear 可映射 face/blur 配方；Miuix 提供双端 Backdrop、shader runtime 与 G2 距离场。应用保留目的地、状态、触感和安全区所有权。
- RuntimeShader、传感器及材质统一通过 miuix-material；普通内容层只录制一份背景，弹层不进入自身采样源。
- 修改的官方源码加 Diy_ 前缀，保留原包名、公开函数和 @file:JvmName 门面；未修改源码使用固定发布归档生成。
- G2 的裁剪 Path、光学距离场、遮罩、阴影和高光共享控制点，真圆/胶囊保持圆弧。
- [圆角模块](../ui-shapes/README.md)、[材质模块](../ui-material/README.md)、[鸣谢](../third-party/README.md)。

## 5. 历史来源与验证范围（0.9.4）

- Miuix release：https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4
- 版本目录：https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/gradle/libs.versions.toml
- 构建设置：https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/build-plugins/src/main/kotlin/BuildConfig.kt
- Scaffold：https://compose-miuix-ui.github.io/miuix/components/scaffold.html
- 固定版 Scaffold：https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/docs/components/scaffold.md
- Compose兼容：https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- 核心参考：faceair/clash-speedtest（1e1fb9e）、sinspired/subs-check-pro（e53554f）。

KMP依赖需先查Klibs，再核对上游许可证与最低系统。所有可查询入口集中在[资料索引](references.md)，不依赖本地调研克隆目录。

已完成：共享五页、双端宿主、Miuix全模块、固定直接依赖、Wrapper与本地检查。9930858已通过双端Release和8项共享测试；本轮悬浮层与生产源码清理尚未编译。Go桥接、塔台存储、最低系统真机性能与最终发布许可仍未完成。

## 6. 历史记录：Miuix 0.9.4 全模块与完整图标

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

## 7. 文件日志（2026-11-06）

选用 `io.github.oshai:kotlin-logging:8.0.5`，业务直接调用 `logger.info/debug`。启动时统一选择 `DirectLoggerFactory` 和文件 Appender；现有 Okio 3.18.2、coroutines 1.11.0、serialization-json 1.11.0 提供后台文件处理。设置与查看只有“信息”“详细”两档，详细包含信息；默认单文件8 MiB、总128 MiB、7天，三项可持久化自定义，总量不能小于单文件。

- Klibs `searchProjects/getLatestVersion` 查到 oshai/kotlin-logging、AndroidJvm、ios_arm64、ios_simulator_arm64，索引最新稳定8.0.4（Kotlin2.1.21）。随后核实 [8.0.5正式发布](https://github.com/oshai/kotlin-logging/releases/tag/8.0.5) 与 [Maven模块元数据](https://repo.maven.apache.org/maven2/io/github/oshai/kotlin-logging/8.0.5/kotlin-logging-8.0.5.module) 已提供三个目标。库Apache-2.0、Android min21；项目维持API33/iOS16/Kotlin2.4.20。README仍标多平台实验性；本次组合的双端编译待Actions验收。
- [Direct输出同步调用Appender](https://github.com/oshai/kotlin-logging/blob/8.0.5/src/commonMain/kotlin/io/github/oshai/kotlinlogging/internal/KLoggerDirect.kt)，因此Appender仅限长复制和非阻塞入队，不做磁盘IO。后台批量写JSONL，队列有界，过载计数可见；单条与单页读取均有限额。文件目录：Android `noBackupFilesDir/logs`，iOS Application Support/logs并排除备份。
- [Kermit 2.2.0文件writer](https://github.com/touchlab/Kermit/blob/2.2.0/kermit-io/src/commonMain/kotlin/co/touchlab/kermit/io/RollingFileLogWriter.kt)队列满时阻塞调用线程、逐条flush，未提供本项目所需分页/清理协调。Napier主要提供日志门面；MVIKotlin的Logging属于Store调试，均不引入。
- 日志查看采用反向游标，每次最多读取256 KiB、返回200条，页面只保留当前页正文。敏感URL仅显示协议/主机/端口，不保存正文、凭据或原始异常消息。清理由同一文件队列串行处理，不回写清理前已排队记录。
