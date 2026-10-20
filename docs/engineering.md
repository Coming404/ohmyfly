# 工程、视觉与验证方案

本文描述当前最小工程和后续实施方案。已创建结构不等于已通过云端编译。需求优先级：用户决定 → 项目规则 → 锁定版本的官方文档/源码。不得擅自替换 Miuix 或增加桌面端。

## 1. 两种“脚手架”分开理解

- **项目工程脚手架**：Gradle、模块、Android/iOS 入口、依赖与工作流。
- **Miuix Scaffold**：页面布局容器，提供顶栏、底栏、浮动按钮、弹层等插槽。它不是项目生成器，不决定业务架构。

首选工程底座为官方 Kotlin/KMP-App-Template。保留 shared、androidApp、iosApp、Wrapper、版本目录，裁剪 desktopApp、webApp 和展品演示业务。删除不必要的 Koin/Coil/Ktor 示例接入，但不为“精简”破坏平台启动代码。是否引入这些库由实际需求决定。

备选为官方 Kotlin Multiplatform Wizard，选择 Android+iOS 和共享 Compose UI。当前在手机/iSH 工作时，GitHub 模板更便于检查和移交 Actions。Miuix example 只作视觉/API参考，不整包复制：它包含更多平台、构建逻辑和不符合本项目的 iOS 示例部署设置。

## 2. 建议工程形态

```text
ohmydrift/
├── androidApp/                 Android Activity、Manifest、系统能力
├── iosApp/                     Swift 宿主、Xcode 工程、签名配置
├── shared/
│   └── src/
│       ├── commonMain/kotlin/
│       │   ├── app/            App、主题、导航
│       │   ├── feature/        订阅、节点、测速、设置
│       │   ├── core/           业务模型、仓库接口、状态
│       │   └── designsystem/   页面令牌与少量业务组件
│       ├── commonMain/composeResources/
│       ├── commonTest/
│       ├── androidMain/       Go Android 桥接、文件与安全存储
│       └── iosMain/           Go Apple 桥接、文件与安全存储
├── native-core/                Go 封装与必要补丁，独立 go.mod
├── gradle/libs.versions.toml
├── gradle/wrapper/
├── .github/workflows/
└── docs/
```

早期一个 shared 模块加 Go 核心即可，不先拆大量空模块。最终目录服从所选模板及 AGP/KMP 插件要求，不能把示意树当作已通过的构建配置。

## 3. Miuix 页面布局

入口使用 MiuixTheme；页面骨架使用 `top.yukonga.miuix.kmp.basic.Scaffold`，不因名称相同误导入 Material Scaffold。

- 顶栏/底栏和主要控件优先使用 Miuix 对应实现。
- 内容正确应用 Scaffold 提供的 PaddingValues；不要在多个层级重复添加系统边距。
- Miuix OverlayDialog、OverlayListPopup 等需要位于其 Scaffold 弹层宿主范围内。
- 顶栏滚动时按文档接入 nestedScroll；键盘、底部手势区、刘海横竖屏分别验证。
- Scaffold 只解决页面结构，不决定字体、栅格、页面间距或数据流。
- 文档与 API以锁定的 0.9.4 标签为准，不能直接复制 main 分支新组件。
- 设置子页当前使用已固定的miuix-nav 0.9.4 NavDisplay与可保存NavBackStack；五个一级入口仍互斥切换，底栏外观与路由运行时分开。路由键复用SettingsPage枚举的稳定名字，启用同Kotlin版本的序列化插件与上游同版JSON1.11.0；不将草稿/凭据放入导航栈，也不改DataStore格式。旧三端SettingsBackHandler已删除，系统/预测返回共享库事件链，镜像编辑优先关闭/取消保留；决定和未实测边界见[设置路由](../.agents/notes/implemented/architecture/2026-10-20-settings-navdisplay.md)。
- 用户已要求引入 miuix-blur 0.9.4，Android 最低版本相应提升到 API33 / Android13；iOS 保持16.0。

## 4. 视觉规则

Miuix 已提供颜色、文字层级和组件 Defaults，项目只补页面级规则，不再造一整套平行主题。

**概览页按锁定版官方 example 惯例与组件 Defaults 实施；不是强制的整页模板：**

| 用途 | 当前概览采用 |
|---|---|
| 卡片外侧、卡片间隔 | 12dp，参考同tag example |
| 分组标题 | SmallTitle 默认28dp/8dp 内距、14sp Bold |
| 标准列表行 | BasicComponent 默认16dp内距、17sp Medium标题与14sp说明 |
| 卡片内部 | 标准行自己提供内距，避免给Card再次叠加 |
| 图标 | 业务线条图标24dp，不替换组件内部图标默认值 |
| 圆角 | Card 默认16dp |

节点库保留卡片列表与选择语义；运行时不包含固定假节点或假延迟，初始为空。禁止通过全局density/fontScale缩放掩盖布局问题。用户已选择系统动态来源：Android提供系统动态代表色，以MonetSystem＋非空动态种子＋Neutral＋Spec2025生成；iOS以System使用Miuix默认浅深色，不读取壁纸或使用固定种子。五页使用同一全屏采样源，顶底仅在小胶囊内采用20f模糊、浅0.22/深0.28表面覆盖、noiseCoefficient=0；不透明回退一致。参数是项目取舍，不是Apple材质规范；详见[UI实现](ui-showcase.md)。

- 文字优先引用 `MiuixTheme.textStyles`，仅为测速大数字等明确用途增加样式。
- 默认使用平台适配字体；不擅自打包小米专有字体或把字体授权视为组件库授权的一部分。
- 系统字体放大、长名称、中英文混排、深色模式必须检查；信息不只靠颜色区分。
- 延迟、吞吐、状态使用稳定语义；单位与数值层级清晰，数值刷新不引起布局抖动。
- 不把所有 dp/sp 常量机械抽成命名常量。抽出重复或有产品语义的值即可。
- 不用注解伪造稳定性；只有满足契约时使用 @Immutable/@Stable。
- 共享视觉不等于共享所有系统行为；iOS 返回/文件选择/分享与权限走平台适配。

设计流程：先定义最少页面令牌 → 使用 Miuix 实现 → 对照真机截图检查对齐、层级与状态 → 按 Apple HIG 检查 iOS 系统交互。

## 5. Go 桥接

节点代理连接、吞吐与统计继续由Go负责；GitHub镜像的有界公开小资源HTTP探测是独立控制面操作，不搬运节点测速负载，也不因此提前接入Go核心。设置使用DataStore Preferences存非敏感配置，平台路径与安全边界见[设置与镜像](settings.md)。

先验证 gomobile 窄接口生成 AAR / Apple framework 或 XCFramework，再由 Kotlin Android、Kotlin/Native 接入。若真实阻碍出现，才评估自定义 C ABI；未构建不能宣布某条路线一定可行或不可行。

建议最少接口：初始化沙盒目录、导入配置、启动任务、取消任务、接收事件、释放资源。内部复杂 map/代理对象不直接跨语言暴露。

事件契约包含 sessionId、nodeId、事件类型、数值单位与错误码；Kotlin 将回调转换为 Flow/StateFlow。时间值显式定义毫秒，避免 Go time.Duration 默认 JSON 纳秒语义。不要在回调中直接改 UI 或让主线程执行阻塞测速。

不要把修正解析结果再送入旧 LoadProxies 后重复按 server:port 删除。Mihomo 版本只能维护一套，不同时打包两套核心。提取上游代码后保留来源、补丁、测试和许可证。

## 6. Actions 与本地边界

**用户已明确：完整构建交给 GitHub Actions。本地 JDK 17 保持现状，不作为工程版本限制或验收阻碍。**

- 本地：源码结构、差异、文档与配置检查；可运行的轻量单测按需执行。
- 不要求本地下载 Gradle distribution、Android SDK/NDK、Kotlin/Native 全量缓存。
- 仓库保留 Wrapper 和校验配置，Actions 使用 Wrapper，禁止依赖 runner 全局 Gradle版本。
- 快速 CI：格式/静态检查、共享业务与 Go 单测、依赖解析；按修改路径组织任务。
- Android CI：Ubuntu + 固定 JDK + SDK → 测试证书签名的 Release APK/AAB；Go 尚未接入，不把 Go 构建当成已完成。
- iOS CI：macOS + 固定 Xcode/JDK → iphoneos/arm64/Release 归档并仅上传待用户重签的 IPA 原文件，归档/符号只保留为 CI 中间结果；不分发旧 Debug 模拟器产物。具体边界见 [真机 Release 构建](release-builds.md)。
- 工作流的 JDK、Go、Xcode、SDK、Gradle等必须固定或受控，不能因“本地忽略版本”而在云端任意使用 latest。
- 缓存按平台/工具链/依赖锁区分；产物设置短保留期。macOS完整打包可手动触发，避免每个文档改动都启动。
- PR 不向不可信代码暴露签名与发布 secrets；fork PR 只做不依赖秘密的检查。

## 7. 验证门槛

P0：双端 App + Miuix Scaffold 可构建；iOS16/API33约束可满足。

P1：Go 库双端链接成功；启动、事件、取消、连续多次任务无崩溃/泄漏。

P2：订阅格式样本、去重碰撞、单节点测速、错误隔离与流量提示通过。

P3：深浅色、小屏/大字体/长名称/横竖屏真机截图验收。

P4：按选定分发方式完成签名、许可证及源码交付。编译成功不等于 iPhone 可以直接安装；iOS16支持不能只通过最新系统模拟器运行来证明。
