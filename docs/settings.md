# 设置与 GitHub 镜像

本轮范围是设置界面、非敏感配置持久化和独立镜像 HTTP 探测；节点 Go 核心与流媒体／AI 探针不在本轮接入范围。保存检测偏好不等于能力已接通。

## 页面结构

设置首页提供测速与延迟、流媒体与 AI、订阅与过滤、GitHub 镜像、外观、数据与隐私、关于七个入口。子页使用显式返回，Android 接系统返回；iOS 不宣称具备原生边缘返回手势。五页主导航与共享 Miuix 主题不改架构。

- 延迟节点并发默认 8（1–32）；单节点下载／上传连接数默认 4（1–8）；吞吐节点并发固定 1。
- 流媒体节点并发默认 2（1–8），平台任务预算不能与节点数／HTTP连接数混用。视频和 AI 项目分组配置。
- 延迟超时、传输超时和最大延迟达标阈值分离；体积为 MiB，速度阈值为 MiB/s，HTTP 请求失败率不称 ICMP 丢包。
- 外观立即生效；尚未接入的节点任务参数仅保存供以后任务使用。表单数值／网址验证后保存，错误不悄悄改成另一个值。
- 恢复设置保留订阅、节点、自定义镜像和历史；当前不提供不存在的历史删除、日志导出或假存储占用。

## 本地存储

使用 `androidx.datastore:datastore-preferences:1.2.1`，不是 SQLite／Room。普通参数用强类型 preferences key，镜像用有界索引字段存储，不为小配置新增 JSON 序列化依赖。

- Android：`context.filesDir/datastore/settings.preferences_pb`。
- iOS：`Library/Application Support/ohmydrift/settings.preferences_pb`。
- 存储是 App 私有沙盒，不是仓库、公共下载或相册。Android Manifest 已关闭应用备份；iOS 目录可能进入系统备份。
- DataStore 本身不加密。订阅 Token、代理密码、私钥不在本配置范围；未来使用安全存储需另定 Keychain／Android 密钥策略。
- 同一路径必须只有一个 DataStore 实例；原子 read-modify-write，读取／未来 schema 失败不覆盖原文件，写入失败不显示成功。

## 镜像交互契约

- 内置候选来自上游配置，初始均“未测试”，不代表可用／可信。打开页面和保存地址不探测，只有用户按测试按钮发请求。
- 自定义 HTTPS 前缀或编码 `{url}` 模板，提供改写预览、规范化去重。前缀原样附原URL，模板编码URL，不能把两者混成一套拼接。
- 直连作为不可删除基准；批量测试有限并发，支持单项、停止和保留已完成结果。
- 测同一固定公开 GitHub 内容，获取有界完整内容并核对 SHA-256；记录 HTTP 首字节耗时，不将主页200、HEAD成功或零毫秒当成通过。
- 成功结果按延迟实时低到高／高到低排序；失败／未测保留明确状态，手动顺序独立持久化。编辑和确认期间暂缓移动；稳定ID维持选择。
- 手动选择优先，真实资源请求失败后按持久化列表顺序降级；显示实际使用路线，不因探测排序改写用户选择。取消不触发降级。
- 公共镜像不接收 GitHub Authorization、订阅令牌或 Cookie，不默认代理私有仓库。内容样本正确不等于运营方可信；实际资源需独立完整性验证。
- 默认样本为 github/gitignore 固定commit `29d931584421f77a7d0bdaa43b0b75be50fae435` 的425字节Kotlin.gitignore（CC0），主会话独立核对SHA-256；只证明raw资源类型，不暗示同一镜像的release/archive也可用。
- Android在OkHttp DNS回调返回经过验证的同一批地址，避免检查后重新解析。iOS预查公网DNS，但NSURLSession会自行解析，**不能保证完全防DNS复绑定**；仅用于用户主动选择的公开镜像，无凭据。原生DNS解析也受系统超时约束，取消和5秒预算不能保证立即中断底层解析调用。
- 读屏焦点目前无法从common可靠检测；只实现稳定key及操作期间冻结排序，不声称已完成所有焦点保护。跨网络结果与离页结果不持久化，重新进入显示未测试。

## 新依赖核对

- klibs 查询官方 androidx/datastore 项目，确认 `androidx.datastore:datastore-preferences`、Android＋ios_arm64＋ios_simulator_arm64，稳定1.2.1；不使用1.3 RC。Google Maven POM指向官方AndroidX发布页，Apache-2.0。
- `kotlinx-coroutines-core/test:1.11.0`：klibs确认稳定及JVM／iOS目标，Maven POM指向Kotlin官方仓库，Apache-2.0。直接使用Flow／取消／测试，因此显式声明，不依赖Miuix传递。
- `com.squareup.okio:okio:3.18.2`：klibs稳定版与JVM1.8／双iOS目标。Maven POM的仓库已迁移至lysine-dev；GitHub `square/okio` API重定向确认同一维护迁移，不把域名变化当成另一个库。Apache-2.0，用于DataStore路径等公开API。DataStore1.2.1自身依赖Okio3.9.1，显式版本解析与完整兼容仍以双端构建验证。
- `io.ktor:ktor-client-core/okhttp/darwin:3.6.0`：klibs确认ktorio项目、稳定版与Android／双iOS目标，Maven POM为Apache-2.0、Kotlin2.3.21元数据（项目2.4.20）。只引HTTP核心和各自平台引擎；mock仅放commonTest。固定版本源码核对`prepareGet.execute`流式读取、`readAvailable`、OkHttp Builder配置和Darwin `configureSession`，Darwin代理明确不自动跟随重定向。官方支持页未给本制品精确iOS下限，本轮不声称已证明iOS16兼容，保留编译／真机验收。
- Android系统返回复用项目已有 `androidx.activity:activity-compose:1.13.0`，不为设置再添加全局导航框架。原生版本通过PackageInfoFlags（API33已满足）和NSBundle读取，不写假统一版本号。

来源：[DataStore多平台官方文档](https://developer.android.com/kotlin/multiplatform/datastore)、[DataStore发布](https://developer.android.com/jetpack/androidx/releases/datastore#1.2.1)、[协程](https://github.com/Kotlin/kotlinx.coroutines)、[Okio](https://github.com/lysine-dev/okio)。

## 验证边界

本地结构／Python检查不是Kotlin编译器或真机网络验证。当前没有授权提交、推送或Actions构建；镜像可用性、最低系统、UI键盘／大字体与网络取消仍需后续编译与设备验收。实现状态与具体测试数量以本轮最终汇报为准。
