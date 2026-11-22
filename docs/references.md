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

## 订阅用量与到期信息

- [ZeroDeng01/sublinkPro 固定源码](https://github.com/ZeroDeng01/sublinkPro/tree/34e93e695e73ea34f3a1e14a742e41ff10891a66)：移植 `sub.go` 的 `ParseSubscriptionUserInfo` 响应头解析，固定于 2026-11-04 的提交 `34e93e695e73ea34f3a1e14a742e41ff10891a66`。MIT，Copyright (c) 2025 ZeroDeng；完整许可见 [sublinkPro-MIT.txt](../third-party/licenses/sublinkPro-MIT.txt)。
- `SubscriptionUsage.kt` 独立承载四字段模型和解析入口。现有订阅 GET 复用同次响应的 `subscription-userinfo`，随最终选中的 UA 快照传入根塔台；provider 用量不替代根用量。现有添加、刷新自动更新，不增加网络请求或 UI 入口。
- 与上游的差异：缺失、负数、超出 Long 范围和格式错误的字段为 `null`，合法 `0` 保留，不使用 `total=-1` 哨兵；字段名区分大小写，后面的合法重复值覆盖前值。`expireEpochSeconds` 原样保留 Unix 秒，`0` 可表达未设置到期。全部字段无效时返回 `null`，不派生已用量、剩余量或时间显示。
- GitHub 域名统一按 URL 的 host 匹配 `github.com`、`githubusercontent.com` 及各自任意子域，规范化大小写和末尾点，拒绝后缀伪装。上述订阅使用单个固定 Mozilla/Chrome 120 UA，不轮换设置中的 UA 候选；普通订阅默认按 `mihomo/1.19.33`、`v2rayN/7.20.4`、`clash-verge/v2.5.2` 顺序比较结果，设置输入框直接填入这三个值，用户可编辑。UA 始终按原地址选择，provider 显式请求头保持优先。
- 真实 `mirrorEnabled`、镜像列表和选中 ID 由 App 经塔台页、Store 传入 Importer。关闭时直连；开启时仅公开 GitHub 资源按选中镜像、其余列表、原站排列。每条线路先按既有 retryable 判定及设置次数重试，再进入下一线路；成功解析即停止后续线路，取消立即传播。所有线路、UA 与 provider 共用 `min(timeoutSeconds * 4, 60秒)` 导入预算，每次请求上限仍为设置超时与剩余预算的较小值。
- GitHub 域名分类不等同于资源路径或跳转授权：公开转发仍要求 HTTPS、默认端口、无凭据/query/fragment 以及受支持路径；新增 Gist `/用户/id/raw` 及其后续文件路径，普通 Gist HTML 页不转换为 raw。Authorization、Cookie 和未知自定义头只送原站；仅 UA、Accept、Accept-Encoding、Cache-Control 可随公开请求转发。镜像线路复用平台既有安全客户端、DNS 与逐跳白名单，订阅保留自身大小上限和同次 GET 的名称/用量头。
- 请求的 UA 候选选择、重试、超时、字节上限和 HTTP 错误处理沿用 OhMyFly 现有实现，不移植 `usage.go` 的独立 HEAD/GET 查询。普通订阅空设置时，`SubscriptionFetcher.kt` 从 `SubscriptionSettings().userAgent` 取得默认 UA 列表；设置页与网络兜底共用同一默认值。刷新失败保留上次成功的节点与用量；刷新成功但响应无有效用量头时清空旧用量。用量随节点与名称在 SQLite 刷新事务中一起保存。

## 订阅名称补全

- `SubscriptionName.kt` 解析同次 GET 的名称元数据，按 `profile-title`（普通文本或 `base64:` UTF-8）→ `Content-Disposition` 的 UTF-8 `filename*` → `filename` → Base64／明文正文首行 `REMARKS=` 排序。`filename*` 支持语言字段与百分号编码，优先于普通 `filename`；普通参数由 Ktor 解析引号和转义，适用于 `inline` 与 `attachment`。正文只检查有界前缀，避免把节点 fragment、节点名称或 YAML 字段误当订阅名称。保留扩展名，只清理外部标题首尾空白；坏编码、控制字符、空值及损坏引号不会使节点导入失败。GlaDOS 示例响应为 `text/plain`，没有 HTML `<title>`、`profile-title` 或 `Content-Disposition`，解码后首行 `REMARKS=GlaDOS` 是名称来源。
- 名称与用量一样归属于最终选中的根响应，不从其他 UA 候选或 provider 拼接。`CatalogRefreshService.add(address, settings, name = "")` 接收调用方已有名称；非空名称原样保留，并通过 `resolveName=false` 跳过名称响应头解析，节点与用量照常读取；provider 请求同样跳过名称解析。空白名称在刷新结束后补入远端名称，获取失败则取原始订阅 URL 的 `host`，不使用重定向目标、路径、凭据或节点名。域名回退一旦成为非空名称，后续刷新也不覆盖。
- 名称与节点、用量在SQLite刷新事务中一起保存；尚无 UI 名称编辑入口，不增加网络请求。
- 行为参考：[Clash Verge Rev 官方 URL scheme 指南](https://www.clashverge.dev/guide/url_schemes.html)、[Hiddify 官方 profile parser](https://github.com/hiddify/hiddify-app/blob/956177a3/lib/features/profile/data/profile_parser.dart)、[RFC 6266](https://www.rfc-editor.org/rfc/rfc6266.html) 与 [RFC 8187](https://www.rfc-editor.org/rfc/rfc8187.html)。`profile-title` 与 `Content-Disposition` 之间的优先级是本项目约定；`filename*` 优先于 `filename` 来自 RFC 6266。未复制 Hiddify 实现。
- API 依据：[Ktor 3.6.0 ContentDisposition](https://github.com/ktorio/ktor/blob/3.6.0/ktor-http/common/src/io/ktor/http/ContentDisposition.kt)、[Ktor 3.6.0 参数解析](https://github.com/ktorio/ktor/blob/3.6.0/ktor-http/common/src/io/ktor/http/HttpHeaderValueParser.kt)、[Kotlin 2.4.20 Base64](https://github.com/JetBrains/kotlin/blob/v2.4.20/libraries/stdlib/src/kotlin/io/encoding/Base64.kt)。

## 航班国家名称识别

- 补充别名来自 [sublinkPro 默认国家规则](https://github.com/ZeroDeng01/sublinkPro/blob/a4565b05f7b37f363575bea5ea6ba745e1f0789f/models/db_migrate.go#L1233)，固定提交 `a4565b05f7b37f363575bea5ea6ba745e1f0789f`，MIT，Copyright (c) 2025 ZeroDeng；沿用 [完整许可](../third-party/licenses/sublinkPro-MIT.txt)。
- `region_catalog.go` 维护现有 248 项国家／地区名称及 CloudFlare 标签；`region_aliases.go` 收纳去重后的城市、别称和非标准缩写；`region_match.go` 使用 Go 标准库进行最长文本及边界匹配。上游预置表达式的有效内容是关键词并列和字母边界，因此不需要新增正则依赖、可编辑规则表或服务端缓存。
- 保留 `LA` 的老挝代码含义，不采用上游的洛杉矶缩写；从输入国旗标记读取原始国家代码，不进行地区重映射。匹配结果通过现有 `CountryFlag` 使用 kmp-flagkit 显示。识别优先级与数据库 v2 升级约定见 [节点地区](towers-flights.md#节点地区)。

## Miuix

- [v0.9.5-beta03 发布](https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.5-beta03)：用户明确选择的预发布版本，固定提交 `3526cfe2ca0779c5a2eab99af8737a96c9a8a885`。
- [固定版组件源码](https://github.com/compose-miuix-ui/miuix/tree/3526cfe2ca0779c5a2eab99af8737a96c9a8a885/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp)与[玻璃组件](https://github.com/compose-miuix-ui/miuix/tree/3526cfe2ca0779c5a2eab99af8737a96c9a8a885/miuix-glass/src/commonMain/kotlin/top/yukonga/miuix/kmp/glass)：精确签名以发布源码为准。
- [材质指南](https://github.com/compose-miuix-ui/miuix/blob/3526cfe2ca0779c5a2eab99af8737a96c9a8a885/docs/zh_CN/guide/material.md)与[图标指南](https://github.com/compose-miuix-ui/miuix/blob/3526cfe2ca0779c5a2eab99af8737a96c9a8a885/docs/zh_CN/guide/icons.md)：使用 material/glass/glass-icons 替代旧 shader 与扩展图标依赖。
- [ThemeController](https://github.com/compose-miuix-ui/miuix/blob/3526cfe2ca0779c5a2eab99af8737a96c9a8a885/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/ThemeController.kt)：液态玻璃档使用 Neutral 并忽略已保存手选色，Android自动使用系统动态种子；Expressive 档使用 ThemePaletteStyle.Expressive，配色选择在此模式显示，iOS自动回退产品蓝，手选配色作为 Monet 种子。
- [统一连续曲率模块](../ui-shapes/README.md)与[玻璃 G2 补丁](../ui-material/README.md)：普通控件和光学边界共用 Shapes1.2.1 几何；修改官方文件加 `Diy_` 前缀，保持公开签名与 JVM 门面。
- [固定版导航](https://github.com/compose-miuix-ui/miuix/tree/3526cfe2ca0779c5a2eab99af8737a96c9a8a885/miuix-nav)：设置子页继续用 NavDisplay，一级入口状态仍由 App 持有。
- [版本目录](https://github.com/compose-miuix-ui/miuix/blob/3526cfe2ca0779c5a2eab99af8737a96c9a8a885/gradle/libs.versions.toml)：Kotlin2.4.21、Compose1.13.0-rc01、serialization1.13.0、AGP9.5.2；最低系统版本继续使用项目要求。

## 玻璃材质实现来源

玻璃统一采用 Miuix `miuix-glass` / `miuix-material` / `miuix-blur`。普通菜单选择使用支持表面插槽的 OverlayCascadingListPopup/OverlayIconCascadingDropdownMenu；弹窗、抽屉接官方 glassDialogSurface/glassBottomSheetSurface。Expressive 档使用不透明 Miuix 表面。

旧 Kyant Backdrop、Melox 玻璃预设、自定义透镜与导航手势已移除；Shapes1.2.1 仅保留为 G2 几何依赖。[鸣谢及许可](../third-party/README.md)、[固定材质源码归档](../third-party/miuix-material/README.md)。

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
- [clash-speedtest](https://github.com/faceair/clash-speedtest)；[审查版本的起飞/加载](https://github.com/faceair/clash-speedtest/blob/1e1fb9e2bbb29282cce5613a6c6b946b85169fdc/speedtester/speedtester.go)。
- [subs-check-pro](https://github.com/sinspired/subs-check-pro)；[审查版本解析目录](https://github.com/sinspired/subs-check-pro/tree/e53554f9ad2cc9770f483f0c08b7d9ea911c331d/proxy/parse)；[去重实现](https://github.com/sinspired/subs-check-pro/blob/e53554f9ad2cc9770f483f0c08b7d9ea911c331d/utils/dedup.go)。
- [Mihomo仓库](https://github.com/MetaCubeX/mihomo)；[协议配置文档](https://wiki.metacubex.one/config/proxies/)；[许可证](https://github.com/MetaCubeX/mihomo/blob/Meta/LICENSE)。
- [原始去重函数边界测试记录](evidence/dedup-results.txt)：只证明去重行为，不是完整项目编译或真实网络测试。

## 构建与持续集成

- [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)。
- [Gradle版本目录](https://docs.gradle.org/current/userguide/version_catalogs.html)；[依赖锁](https://docs.gradle.org/current/userguide/dependency_locking.html)；[依赖校验](https://docs.gradle.org/current/userguide/dependency_verification.html)。
- [Actions文档](https://docs.github.com/en/actions)；[托管runner软件镜像](https://github.com/actions/runner-images)。
- [setup-java](https://github.com/actions/setup-java)；[setup-gradle](https://github.com/gradle/actions)；[setup-go](https://github.com/actions/setup-go)。

macOS镜像和预装Xcode会变化；不能把模板中的旧路径当作永久可用。iOS模拟器构建不等于已签名IPA或最低系统真机验证。
