# Agent Note: 持久化设置与真实 GitHub 镜像探测

Status: implemented

## Problem

设置仅有会话内外观状态，没有真实参数存储和镜像探测。用户确认实施“设置＋镜像实测”、流媒体＋AI，镜像手动选择且失败后依次降级；没有授权提交或构建。

## Decision

- 用单一共享设置模型和 DataStore Preferences 1.2.1（klibs 已核对 Android、ios_arm64、ios_simulator_arm64 与稳定版本）存非敏感参数及镜像配置，不上 SQLite，不存订阅凭据。
- Android 位于应用 filesDir/datastore，iOS 位于 Library/Application Support/ohmydrift。异步读写和原子更新；读取失败不以默认值覆盖原件，保存失败反馈。
- 设置七入口，参数校验、草稿保存、重置、主题即时生效；节点和媒体参数只是配置层，不虚构引擎能力。
- 镜像 HTTP 探测是独立网络操作，不承载节点测速负载。小型固定公开资源＋哈希校验，有限并发、超时、取消、真实 TTFB 排序。
- 手动选定项不被排序切换；资源请求失败后按持久化手动顺序降级，禁止凭据转发与静默第三方请求。

## Existing decisions audit

- [生产UI清理](../../implemented/simplification/2026-10-19-production-ui.md)：部分重叠，保留无假数据边界；仅设置／镜像新能力替代其“无持久化”的现状。
- [UI初始状态](../../implemented/feature/2026-10-18-ui-showcase.md)：部分重叠，会话内主题由持久化替代；展示阶段的历史理由保留。
- [工程基础](../../implemented/architecture/2026-10-18-project-foundation.md)：原来不引入存储是无实际需求，当前已获明确授权；不引入DI／数据库／新前端。

## Alternatives considered

- SQLite／Room：适合节点与历史查询，但设置和少量镜像不需要关系数据库，增加本轮迁移面。
- SharedPreferences＋UserDefaults：平台原生且少依赖，但需两套写入语义；官方多平台 DataStore 提供统一事务与 Flow。
- 自动选最快镜像：操作少，但违背用户手动选择；仅失败降级，不因探测排序改变选择。

## Build follow-up

用户后续授权提交与双端Release，且每次重建前清理旧Actions记录。首轮真实编译暴露两项静态检查不能发现的问题，现按官方API修正并增加回归守卫：平台顶层文件采用`MirrorTransport.android.kt`／`.ios.kt`，避免JVM facade重名；macOS构建分支用`iosMain.dependencies`惰性访问器添加Darwin，禁止`findByName()?.`静默漏配置。共享测试门槛提高为62项，不用旧的6项门槛掩盖漏跑。HTTP边界测试将MockEngine显式绑定`StandardTestDispatcher(testScheduler)`，避免真实IO线程与虚拟超时竞速；保留原超时和全部断言。重定向解析先拒绝非HTTPS绝对scheme，不将`http:`误作同源相对路径。

来源：[Kotlin平台文件命名](https://kotlinlang.org/docs/coding-conventions.html#multiplatform-projects)、[iOS源集访问器](https://kotlinlang.org/api/kotlin-gradle-plugin/kotlin-gradle-plugin-api/org.jetbrains.kotlin.gradle.dsl/-kotlin-multiplatform-source-set-conventions/ios-main.html)。编译结果以最终对应run为准；不下载产物，不修改签名。

## Consequences

- 配置仓库持有schema 1与进程级同路径实例；读取仅回落内存而不写回原件，用户写入在同一DataStore事务内校验。镜像身份、路径大小写和编码模板由同一模型处理。
- 镜像可编辑、单测／批测、停止并保留有效结果；固定CC0公开样本经独立HTTP获取与SHA-256验证，测试夹具只在commonTest。当前线路验证实际执行手动优先降级链，取消不降级；不是一个未引用的helper。
- Ktor3.6.0核心＋OkHttp／Darwin实现有界流式读取，Mock仅测试依赖；版本、klibs、Maven与固定源码证据见[设置说明](../../../../docs/settings.md)。不添加SQLite、DI、全局导航或JSON库。
- 初始五个镜像候选未实测／不背书；打开和保存不会联网。底层DNS解析受系统超时约束，不能承诺瞬时取消；iOS NSURLSession二次解析不能完全钉住DNS，因此不声称完全阻止复绑定，不承载私有资源与凭据。
- 只保证稳定key和编辑期间冻结排序；无法可靠观察读屏焦点，不宣称完整焦点保护。iOS使用显式返回，未增加原生边缘返回手势。
- 本地结构、24项Python与差异检查通过；共享测试共62项声明（包含本轮新增），尚未执行，Gradle／Kotlin、Xcode、DataStore实盘、镜像和最低系统真机验证均未运行。本轮没有提交／推送／触发Actions，也未下载产物或改签名。一次性Ktor源码核验JAR已删除，`.ops/`仅保留既有github.py。
- DataStore不加密，敏感凭据不在本轮存储范围；节点、订阅解析、流媒体和AI实际核心仍待接入。
