# 设置与 GitHub 镜像

本轮范围是设置界面、非敏感配置持久化和独立镜像 HTTP 探测；节点 Go 核心与流媒体／AI 探针不在本轮接入范围。保存检测偏好不等于能力已接通。

## 页面结构

设置首页包含品牌身份区、外观、测试、订阅与网络、应用四组。明暗模式、界面材质弹出选择、减少透明效果与减少动态效果在首页直接修改；测速与延迟、流媒体与 AI、订阅与过滤、GitHub 镜像、数据与隐私、关于保留六个子页入口，删除已被首页替代的外观子页。品牌复用Wave标记及平台实际版本参数，仅保留一行获准的产品简介，不显示配置状态摘要。子页保留纯图标返回、“返回”读屏名称和48dp触区。

设置子页已经接入Miuix NavDisplay 0.9.4：App持有唯一rememberNavBackStack<SettingsPage>(Home)，SettingsPage为可序列化NavKey，SettingsScreen用entry<SettingsPage>注册同一枚举类型的七个值。打开子页只从Home入栈，重复/过期点击不堆叠；标题、返回与底栏显隐由栈派生。五个一级入口仍是互斥切换，按稳定名称保存当前入口，不入设置栈，不改悬浮外观与材质。

正常运动使用官方MiuixDefault转场；有效减少动态时使用None并关闭NavDisplayEffects。正常模式按LTR/RTL分别开启向右/向左物理滑动返回，根页不滑退，减少动态时关闭空间滑退。NavDisplay统一系统/预测返回及页面生命周期，原expect/actual SettingsBackHandler三文件已删除。镜像编辑时禁用页面滑退，标题返回与公共PredictiveBackHandler先关闭编辑器，取消手势不关闭；编辑稿仍只在原页面持有，报告给App的仅是可空关闭动作，离页清理。官方弹层继续用原Scaffold宿主，不添加竞争返回处理器。

可保存栈只恢复导航身份，不能当成将草稿或所有业务状态持久化；系统重建/恢复、iOS16/API33与手势优先级尚未真机验收。它是Compose共享手势，不冒充UIKit原生导航。顶部/底栏仍随已提交逻辑栈更新，未加入逐帧联动动画。

- 延迟节点并发默认 8（1–32）；单节点下载／上传连接数默认 4（1–8）；吞吐节点并发固定 1。
- 流媒体节点并发默认 2（1–8），平台任务预算不能与节点数／HTTP连接数混用。视频和 AI 项目分组配置。
- 延迟超时、传输超时和最大延迟达标阈值分离；体积为 MiB，速度阈值为 MiB/s，HTTP 请求失败率不称 ICMP 丢包。
- 首页明暗模式、界面材质、减少透明效果和减少动态效果先经校验同步反映到界面，再按序通过同一DataStore事务保存。保存失败恢复最后已确认的设置，丢弃失败操作与其后尚未保存的操作，并提示重新选择重试；旧Flow回发不会覆盖较新的选择。四种材质为高斯模糊/高光玻璃/柔光玻璃/液态玻璃，默认液态玻璃；四项共用一条参数化Melox图文采样/折射/高光链及双端传感器适配，尚待编译和真机验收。开启应用或iOS系统减少透明效果、或shader不可用时停止页面采样、走实体回退，不改写已选材质；菜单不再单列关闭。主题、材质和测速默认模式统一使用官方OverlayDropdownPreference：右侧当前值＋常驻上下双箭头，点击弹出选项，当前项对勾，单选后自动关闭；弹层状态和动画由官方组件持有，选择值仍只读AppSettings。其他真实单选行继续复用官方RadioButton，不增加重复焦点。
- 尚未接入的节点任务参数仅保存供以后任务使用。表单数值／网址验证后保存，错误不悄悄改成另一个值。
- 恢复设置保留订阅、节点、自定义镜像和历史；当前不提供不存在的历史删除、日志导出或假存储占用。

## 本地存储

使用 `androidx.datastore:datastore-preferences:1.2.1`，不是 SQLite／Room。普通参数用强类型 preferences key，镜像用有界索引字段存储；本轮NavDisplay引入的JSON序列化只用于库自带导航Saver，不改变DataStore文件或镜像编码。

- Android：`context.filesDir/datastore/settings.preferences_pb`。
- iOS：`Library/Application Support/ohmydrift/settings.preferences_pb`。
- 存储是 App 私有沙盒，不是仓库、公共下载或相册。Android Manifest 已关闭应用备份；iOS 目录可能进入系统备份。
- DataStore 本身不加密。订阅 Token、代理密码、私钥不在本配置范围；未来使用安全存储需另定 Keychain／Android 密钥策略。
- 同一路径必须只有一个 DataStore 实例；原子 read-modify-write，读取／未来 schema 失败不覆盖原文件，写入失败不显示成功。

## 即时反馈与保存恢复

`PersistedSettings`只读取一个随composition生命周期存在的`SettingsSession`快照：点击以最新可见值变换并校验，同步呈现；持久化保留原变换，在DataStore最新事务值上合并，不整份覆盖其他字段。

首个待保存操作取消旧Flow订阅，保存后重新读取已确认值，队列排空才恢复常规订阅；不靠值相等推断写入版本。保存失败丢弃失败后缀并恢复已确认值，重新选择即可重试；协程取消不显示IO错误。提交后确认读取失败会进入只读失败页，不伪称磁盘已回滚。路径/重试/重建更换生命周期会话，旧回调不可继续写入。决定见[即时反馈](../.agents/notes/implemented/feature/2026-10-20-optimistic-settings.md)。

## 材质配置兼容

- `AppSettings.materialMode`默认`MaterialMode.LiquidGlass`；`ui.materialMode`保存`GaussianBlur/HighlightGlass/SoftGlass/LiquidGlass`枚举名，不保存中文标签或序号。
- 本轮仅扩展可选key，schema仍为1。旧配置缺失该字段、未知枚举或错误类型都只在内存回退液态玻璃；不在读取时重写原文件，不覆盖原有明暗、减少透明效果或业务参数。
- 写入继续使用既有DataStore原子事务并保留未知key；恢复默认设置通过AppSettings默认值回到液态玻璃。真机重启恢复与实盘读写仍待验收，决定见[材质选择](../.agents/notes/implemented/feature/2026-10-20-material-selection.md)。

## 运动偏好与切换过渡

- `AppSettings.reduceMotion`默认false，可选布尔key `ui.reduceMotion`；schema仍1，旧配置缺失或错类型只在内存回落，不读取后自动写回。它与减少透明效果/材质独立；恢复默认复用原AppSettings，保存失败也复用原会话回滚。
- 根`AppMotion`读取Compose平台`MotionDurationScale`的可观察值，应用开关开启或系统scale为0时禁用项目自有运动。正数系统倍率不被覆盖：Android继续沿用平台动画缩放，iOS继续沿用Compose对UIKit减少动态效果的原生接入；不另装重复系统观察者、不写系统设置。
- 自有主题全53个语义色及4个页面/卡片角色、单条玻璃链的8个数值参数正常过渡180ms，限制生效直接呈现目标并取消旧过渡。玻璃手势owner随策略替换，取消旧scope、光斑和回弹，保留已提交入口与测量宽度；追光停止采集。
- 设置导航在限制下改用官方`NavTransitions.None`和`NavDisplayEffects.None`并禁用空间滑退；标题/系统返回、编辑器优先级和草稿不改。一级入口仍只渲染当前页面，不加全局Crossfade。None移除可见变换，不承诺库内部运行中的深度驱动当帧终止；设备需检查返回结束与entry清理。
- **覆盖限制**：应用开关没有替换Compose根协程上下文，不能关闭Miuix内置Switch/Dropdown等组件的全部动画；它们仍遵循系统运动限制。固定版没有轻量公共的双端全局禁用接口，本项不为此改写宿主或调用internal API。
- ThemeController继续唯一生成颜色。保留官方controller根以提供动态模式标记，内层唯一colors覆盖仅用于动画并继承该标记，默认内容色显式关联动画中的onBackground；不复制页面、Scaffold或第二控制器。决定见[减少动态与过渡](../.agents/notes/implemented/feature/2026-10-20-reduced-motion.md)。

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
- 设置导航复用已引入的 `miuix-nav:0.9.4`；共享模块已删除只供旧返回适配使用的activity-compose直接依赖，Android宿主自身仍使用1.13.0。序列化插件与项目Kotlin同为2.4.20，JSON库固定1.11.0，与Miuix上游一致。Klibs确认Kotlin官方库、JVM1.8（Android通过JVM制品）与双iOS目标、最新稳定1.11.0，未选1.12.0-RC；AndroidJvm筛选未命中不当成不支持。官方README说明Android/Native支持，Apache-2.0；精确最低系统与最终解析版本仍需双端构建。原生版本读取方式保持PackageInfoFlags/API33与NSBundle。
- `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0`：倾斜光只在前台/可见composition采集所需，直接使用`LocalLifecycleOwner`、`LifecycleEventObserver`与`currentStateAsState`，因此显式声明而非依赖Miuix传递。Klibs确认androidx lifecycle项目含common/AndroidJvm/双iOS目标，报告稳定2.11.0（Kotlin2.3.20元数据，项目2.4.20）；与固定Miuix上游`jetbrains-lifecycle=2.11.0`一致。未做独立许可证核验，沿用AndroidX Apache-2.0声明；双端编译/运行仍待验收。

来源：[DataStore多平台官方文档](https://developer.android.com/kotlin/multiplatform/datastore)、[DataStore发布](https://developer.android.com/jetpack/androidx/releases/datastore#1.2.1)、[协程](https://github.com/Kotlin/kotlinx.coroutines)、[Okio](https://github.com/lysine-dev/okio)。

## 验证边界

本地结构／Python检查不是Kotlin编译器或真机网络验证。当前没有授权提交、推送或Actions构建；镜像可用性、最低系统、UI键盘／大字体与网络取消仍需后续编译与设备验收。实现状态与具体测试数量以本轮最终汇报为准。
