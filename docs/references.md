# 开发资料入口

这里保存可重复查询的上游入口；开发规则在 [AGENTS.md](../AGENTS.md)。入口存在不等于已验证所有API，版本敏感问题必须切换到本项目锁定的release/tag。

## 查询顺序

1. 查看 `gradle/libs.versions.toml`、Wrapper及相关决定，明确当前版本和平台。
2. 新增或推荐KMP依赖前，先查已配置的Klibs MCP，再读官方仓库/许可证/发布记录。
3. **官方入门文档继续作为入口；涉及具体组件时，用项目锁定版本的源码核对，发现个别示例不同步就按源码调整。** UI问题先查Miuix对应版本文档与源码；工具链兼容查Kotlin/Gradle/Android官方资料。
4. 结论注明来源、版本、未验证项。发现过期链接或更换依赖时同步更新本页。

## Klibs 与依赖仓库

- [Klibs检索](https://klibs.io/)：查多平台库、作者、目标平台与坐标。
- [Klibs MCP](https://api.klibs.io/mcp)：使用已配置的`klibs`服务器。当前工具为`searchProjects`、`getLatestVersion`，调用前以实际schema为准。
- [Maven Central](https://central.sonatype.com/)：查制品、POM、版本与元数据。
- [Google Maven](https://maven.google.com/)：AndroidX、Android构建相关制品。
- [Gradle Plugin Portal](https://plugins.gradle.org/)：构建插件及说明。

MCP版本/目标信息不足以证明许可证或持续维护；缺失字段查官方LICENSE、commits、releases、issues。注意同名fork和`latestStableVersion=null`的语义。

## Miuix

- [官方文档](https://compose-miuix-ui.github.io/miuix/)；[中文文档](https://compose-miuix-ui.github.io/miuix/zh_CN/)。
- [官方中文快速开始](https://compose-miuix-ui.github.io/miuix/zh_CN/guide/getting-started)；[英文入门](https://compose-miuix-ui.github.io/miuix/guide/getting-started.html)。
- [Scaffold](https://compose-miuix-ui.github.io/miuix/components/scaffold.html)：页面容器、insets和弹层宿主，不是工程模板。
- [主题](https://compose-miuix-ui.github.io/miuix/guide/theme.html)；[文字样式](https://compose-miuix-ui.github.io/miuix/guide/textstyles.html)。
- [0.9.4 ThemeController](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/ThemeController.kt)：`MonetSystem + Vibrant + Spec2025` 使用显式种子才进入对应生成分支；不把平台默认动态色当成指定风格已生效。
- [组件源码](https://github.com/compose-miuix-ui/miuix/tree/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp)。
- [0.9.4完整文档源码](https://github.com/compose-miuix-ui/miuix/tree/v0.9.4/docs)：网站可能比锁定版本更新，标签内文档也可能滞后；以同版本组件源码或发布 sources.jar 为最终签名依据。
- [0.9.4 基础行 Component](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Component.kt)、[SmallTitle](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/SmallTitle.kt)、[Card 示例](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/example/shared/src/commonMain/kotlin/component/CardSection.kt)：概览页默认排版依据。
- [0.9.4 blur 指南](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/docs/guide/blur.md)、[顶栏模糊用法](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/example/shared/src/commonMain/kotlin/utils/PageUtils.kt)：核对真实的 layerBackdrop / textureBlur，而非套用其他库API。
- [0.9.4 NavigationBar 源码](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/NavigationBar.kt)：使用 `NavigationBar { NavigationBarItem(...) }` 槽位 API，不能照抄旧文档的 `items/selected/onClick` 参数。
- [0.9.4 基础 Checkbox](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Checkbox.kt) 与 [Switch](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Switch.kt)：当前展示使用基础控件；不导入已移除的 `extra.SuperCheckbox/SuperSwitch`，不为演示增加独立 preference 模块。
- [0.9.4发布模块清单](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/settings.gradle.kts)：正式模块为ui/core/preference/icons/blur/shader/squircle/nav，全部同版；example/docs/baselineprofile不属于App依赖。
- [0.9.4完整图标文档](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/docs/zh_CN/guide/icons.md)：完整miuix-icons提供扩展图标与五种权重，Basic由miuix-ui提供；不臆造icons-basic或Useful分组。
- [0.9.4导航模块](https://github.com/compose-miuix-ui/miuix/tree/v0.9.4/miuix-nav)：本版用miuix-nav替代旧navigation3-ui；依赖引入和路由迁移是两项不同任务。
- [0.9.4正式发布](https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4)；[所有发布](https://github.com/compose-miuix-ui/miuix/releases)。
- [0.9.4版本目录](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/gradle/libs.versions.toml)。
- [构建设置](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/build-plugins/src/main/kotlin/BuildConfig.kt)；[模糊模块](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-blur/build.gradle.kts)：查最低SDK。
- [官方example](https://github.com/compose-miuix-ui/miuix/tree/v0.9.4/example)：组件演示与宿主参考，不是独立精简Starter。
- [官方组织仓库](https://github.com/orgs/compose-miuix-ui/repositories)：本次未找到独立官方应用模板仓库；以后可能变化，应再次核对。
- [Miuix许可证](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/LICENSE)。

## Kotlin / Compose / 工程

- [Kotlin Multiplatform文档](https://kotlinlang.org/docs/multiplatform.html)。
- [Compose版本与平台支持](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)。
- [KMP兼容指南](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)。
- [Compose Multiplatform仓库](https://github.com/JetBrains/compose-multiplatform)；[1.12.0发布](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.0)。
- [KMP官方共享UI模板](https://github.com/Kotlin/KMP-App-Template)：本工程Wrapper/iOS宿主来源。
- [官方创建向导](https://kmp.jetbrains.com/)：需要重新生成干净工程时参考。
- [共享资源](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources.html)。
- [Apple framework直接集成](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html)。

## Android / iOS 系统

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
