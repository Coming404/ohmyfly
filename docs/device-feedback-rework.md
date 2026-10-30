# iOS 真机反馈与双端体验修正方案

状态：各阶段进度以 [NOW.md](context/NOW.md#真机反馈阶段) 为准，本文只保存需求与方案。日期：2026-10-20；2026-10-23 追加[第 8 节继续改进需求](#8-继续改进需求2026-10-23)。

基线：仓库 `6d75347`，可执行代码 `78d80bd`；该基线已通过双端 Release 构建及 146 项 Kotlin 共享测试。用户本次 iOS 真机反馈说明：构建通过不等于视觉、触控和性能验收通过。本轮只交付文档，不修改运行时代码、不提交推送、不触发构建。

## 1. 范围与证据

- 保留 Android/iOS 共享 Compose UI、Miuix 0.9.4、五个一级入口、四种材质及现有设置持久化；不改成两套原生页面，不扩展塔台/起飞核心。
- Melox 参照固定 `49ee06aa1f01a67eb5ea1d5cc7307ddfd571a534`，仅移植适用几何与效果，不复制播放器业务或其修改版依赖。
- 截图证据：[底栏放大图](minis://attachments/uploads/photo_FEBA6B7D.jpeg)、[设置全屏图](minis://attachments/uploads/photo_5AFC73BE.png)、[深色子页顶部](minis://attachments/uploads/photo_B3B98A23.jpeg)。保留原图，不以裁图像素直接推断 dp/pt。
- **已确认**表示截图或当前源码可直接证明；**待测假设**表示尚无真机录屏、调用轨迹或性能采样，不能当成已定位根因。
- 本文数字分别标明来源：Miuix 默认、Melox 实现、项目现值、拟定目标。拟定目标不是 Apple 或 Miuix 官方规定；实施时以双端实际布局复核。
- 当前文档承接[原设置改造方案](settings-redesign.md)。原方案中的已实现部分仍是当前代码事实；与本次目标冲突的几何、标题、动效、弹层及表单交互，待实施时逐项替代并同步原记录，不先标成完成。

## 2. 优先级与实施顺序

| 阶段 | 对应原反馈 | 完成目标 |
| --- | --- | --- |
| A：平台与触控缺陷 | 3、5、10 | 状态栏可见；安全区无重复/额外浪费；有效点击立即反馈 |
| B：紧凑布局与字体 | 1、6、8 的视觉部分 | Melox 几何、正常/加粗字重、纯图标返回、设置图标和层级统一 |
| C：玻璃与性能 | 2、4 | 去除多余灰层、按压保持透景；用消融定位首启与掉帧 |
| D：设置即时编辑 | 8 的交互部分 | 去保存/取消；有效值即时应用；镜像页重排且不保存坏输入 |
| E：弹层与运动 | 7、9、12 | 弹层材质统一、轻度背景模糊、短促弹簧、返回不受破坏 |
| F：点击震动与回归 | 11、全部 | 开关与轻中强、默认中；仅有效点击震动；双端验收 |

按阶段实施，每阶段交付当前值/修改后值、验证结果与未通过项，等待确认后再进入下一阶段。先解决点击与帧耗，再加强弹性；不得用更长动画掩盖掉帧。

## 3. 逐项修正

### 3.1 底栏和顶部按钮过大（原反馈 1）

**已确认**：当前底栏标签未选中为 Medium，选中为 Bold；图标 24dp、标签 11sp/14sp 行高、图文间隔 4dp，最小主体高度 64dp。当前子页顶部仍显示“返回按钮＋子页标题”，所以“返回图标没有文字”并不等于已满足本次“去掉右侧文字”。

**目标**：
- 优先采用固定 Melox 的外层/选中胶囊几何及紧凑图文参数；精确对照见后续参数表。不能只把圆角改大而保留过高容器。
- 五项保持等宽，减小容器多余宽度与内距；区分“项之间的空白”和整栏宽度，不新增一层间距。小屏不挤掉完整标签，大字体允许高度增长。
- 未选中 Normal，选中 Bold；字重改变不改变命中区域，不导致邻项位移。
- 子页顶栏只保留返回图标与实际功能动作，移除按钮右侧页名；一级页标题保留。必要的子页名称可作为内容分组标题出现一次，不再重复。
- 返回图形拟缩为 20–22dp，玻璃可见面拟约 36–40dp，实际触控区域 iOS 保留至少 44×44pt、Android 按平台组件触区处理。视觉缩小不等于缩小可点击范围。

**验收**：五入口完整、选中/未选中字重正确；小屏/大字/横屏/RTL 不裁字；返回、读屏名称、键盘焦点及系统返回完整；截图对照不再出现巨大返回胶囊和右侧重复页名。

### 3.2 玻璃灰色覆盖与长按不透明（原反馈 2）

**观察**：四种材质均有蒙灰感，液态玻璃长按后尤其不透明。静态图只能确认外观，不能证明是哪一层合成导致。

**目标**：
- 分开检查底栏基底、选中胶囊、顶部按钮、按压/高光层、内容采样以及系统实体降级；不把所有灰色都归因于 blur。
- 删除多余的实色覆盖、重复叠层和按压时不必要的实色增量；保留必要轮廓、文字对比和不同材质特征。液态/高光偏清透，柔光/高斯保留有意的柔化，不强制四种材质都变成同一块透明板。
- 长按和拖动只改变形变、光泽与必要折射，不把背景过渡成实色。若系统/应用“减少透明效果”开启，则继续遵循实体降级，不能为透明观感绕过无障碍偏好。
- 去除灰膜后若浅背景上选中项难辨，优先靠清晰边缘、位置与选中图文解决，不重新铺大块灰底。

**验收**：浅/深、纯色/文字/复杂背景四种材质对照；静止、按下、长按、拖动、释放录屏；按压前后仍能辨认背后内容，且不会采样到自身造成累积变灰。

**2026-10-30 实现（待真机目测）**：表面覆盖 alpha（浅/深）调整为高斯 0.12/0.16、高光 0.06/0.08、柔光 0.18/0.22、液态 0.08/0.10；删除按压时整块 0.06 白色 Plus 加亮，保留手指位置光斑（用户决定）、形变、色散与内阴影。非液态的选中胶囊会采样隐藏录制层，因此保留四种材质的隐藏录制。

**2026-10-31 真机反馈与改法（未编译）**：纯色背景上液态底栏内部与页面同为 #F0F1F3，只有白色顶缘高光，看起来像一层浅灰膜。原因：染色用 Miuix 浅色 surface（#F7F7F7）乘 0.08，在纯色上几乎不变，在白卡片上反而压暗；纯色背景没有边缘，模糊与折射看不出来。Melox 用 surfaceContainer（浅色为白）乘 0.4。用户选定：浅色模式下四种材质的底栏、顶部按钮和消息卡片都改用白色染色（GlassEffects.glassTint），深色保持 surface；液态浅色 alpha 0.08 改为 0.30（页面 F0 变为约 F4，背后黑字约 #4C），其余材质 alpha 不变。

### 3.3 顶部安全区和首屏留白过大（原反馈 3）

**已确认**：顶栏在系统 safeDrawing 之外还有上下各 8dp 和至少 48dp 主体。iOS SwiftUI 宿主使用 ignoresSafeArea，Compose 顶栏自行消费顶部/横向安全区；仅凭截图不能断言系统安全区被重复计算。标题到品牌、品牌到分组的距离还包括内容布局，不全属于安全区。

**决定**：
- 背景可以铺到刘海/药丸/挖孔周围；交互控件和关键文字必须同时避开系统时间、信号、电量及硬件遮挡。
- 不采用“只避开屏幕中心，左右控件直接顶到状态栏”的统一算法。系统安全区包含硬件与系统 UI；Android 孔位也不保证在中央。
- 先逐层量出宿主、Scaffold、顶栏、列表的实际 inset 与内容 padding，系统 inset 只消费一次；删除冗余品牌区/分组间隔，而不是把所有留白都压到系统状态栏内。
- 紧凑顶栏拟以 44dp 主体、上下 4dp 为首轮目标，系统 inset 另计；最终由双端布局与触区检验，不硬编码机型或刘海高度。
- 2026-10-30 真机反馈：顶栏背后的页面色渐变覆盖到顶栏高度 80%，滚动内容被遮挡，看起来和缩小前一样。用户选择只遮挡状态栏：实色只铺满系统顶部 inset（状态栏、刘海或灵动岛），向下 8dp 渐隐；标题和按钮浮在内容上，内容可滚到灵动岛下沿，和底部悬浮栏的处理一致。

**验收**：iOS 刘海/灵动岛、Android 中央/非中央挖孔、无挖孔；横竖屏、键盘、放大字体、状态栏变化无覆盖。底部继续只消费一次 inset，悬浮栏下的列表最后一项必须能滚到完整可见。

### 3.4 首启卡顿与 60/120Hz 流畅度（原反馈 4）

**待测假设**：首次 shader/字体/图层初始化、玻璃采样和合成、首帧设置读取、传感器更新可能影响耗时；尚不能认定液态玻璃是唯一原因。iOS 已有 CADisableMinimumFrameDurationOnPhone=true，高刷不是补一个开关或安装动画库就能保证。

**方案**：
- 记录冷启动、热启动、首帧与首次可交互时间；滚动/导航/菜单/按压分别测量，比较高斯与液态，以及只移除一个因素的最小消融。
- 优先检查采样面积、重复录制、离屏图层、shader 建立位置、每帧对象分配、无变化仍触发的重组、隐藏/后台传感器。可缓存的编译结果按实际参数复用，不缓存过期背景画面。
- 仅在测量证实首次编译为瓶颈时，评估受控预热/分阶段初始化；不能用启动延时、假加载屏或永久降低材质来掩盖问题。
- 首选 Xcode Instruments 与 Android 官方帧分析工具；不预设新增第三方运行时依赖。若确需新增 KMP 库，另核对 Klibs 的双端目标、版本、许可及收益。

**测量结果（2026-10-30，iPhone13,2，iOS 16.6.1，60Hz，应用内启动记录）**：装新包后首次启动，首帧 412ms 后有一个 851ms 长帧，与设置读取结果同在 1172ms 结束；再次冷启动只有设置到达后的 61ms 长帧；选柔光时冷启动在设置到达时有 104ms 长帧。启动 1.2 秒后帧间隔稳定在 16.7ms。原因：PersistedSettings 先用 AppSettings() 默认值（液态玻璃）组合并绘制首帧，读到设置后整页换材质；首次 851ms 推断为玻璃着色器首次编译 Metal 管线，未直接证实。Skiko 0.150.1 的 DirectContext.makeMetal 不传 GrContextOptions，无法接入 Skia 持久缓存，Skia 运行时生成的着色器也不能用 MTLBinaryArchive 预编译，所以不做打包缓存。

**已改（未提交）**：参照 Melox MeloxViewModel 的 initialSettings，PersistedSettings 在创建会话时用 runBlocking(Dispatchers.IO) 读取 repo.data.first()，作为 SettingsSession 的 initial（settings 与 confirmedSettings 同时设置）；读取期间启动画面保持。同步读取失败时 initial 为 null，回到原有异步读取并由 readFailed 报错。首帧只渲染用户所选材质，只有选液态玻璃时才绘制液态玻璃。

**验收**：相同设备/温度/电量模式下重复测量并记录中位数、P95 帧耗和长帧。60Hz 的一帧约 16.7ms，120Hz 约 8.3ms；按设备实际刷新率评估。目标是支持设备上稳定、响应及时，不能在缺少设备记录时承诺全机型跑满 120fps。

### 3.5 应用深色模式导致状态栏图标不可见（原反馈 5）

**已确认的接线缺口**：共享 App 按设置计算 dark 并变更页面颜色，但 iOS MainViewController/SwiftUI 宿主没有接收应用外观变更；Android 当前 enableEdgeToEdge 也没有显式消费应用手动明暗选择。系统与应用外观分离时，容易出现页面已深色、系统栏仍按浅色绘制的错配。

**方案**：以同一个已生效主题为源同步系统栏；iOS 根据实际宿主所有权选择 SwiftUI preferredColorScheme 或 UIKit 的外观/状态栏更新路径，不让多个层级竞争设置 window。Android 同步状态栏/导航栏前景和边到边行为。切回“跟随系统”时恢复跟随，不改写系统设置，不因联动重建导航栈。

**验收**：系统浅/深 × App 浅/深/跟随系统，逐一测试启动、运行中切换、返回前台、菜单/键盘出现；时间、信号、电量均清晰，Android 底部系统图标也正确。

### 3.6 卡片字号与系统字体（原反馈 6）

**已确认**：当前自定义行常用 16sp 标题/14sp 辅助，官方选择行使用 headline1/body2，基线分别为 17sp/14sp。不能把不同组件的字号层级当成已经统一。

**字体核查**：Miuix 默认 TextStyles 未指定专用字体；固定 Compose 的 iOS 默认字体链已有 `.AppleSystemUIFont → Helvetica Neue → Helvetica`，并由平台字形回退处理其他字符。因此当前没有证据证明“误用了小米字体”；字重、字号、行高、中文回退与 Skia 渲染差异均需比较。

**拟定文字层级（项目值，不是官方默认）**：

| 用途 | 当前/官方基线 | 本轮首选目标 |
| --- | --- | --- |
| 设置行名称 | 自定义 16sp；部分官方选择行 17sp | 统一 15sp Medium（2026-10-30 用户由 Normal 改为 Medium）；图标与名称间距统一 8dp，与 BasicComponent 一致 |
| 右侧值/输入内容 | 14sp 或组件自身样式 | 14sp，保持与主标题明显层级 |
| 必要辅助/校验信息 | 部分自绘内容继承主样式 | 辅助信息 11sp 浅灰（onSurfaceVariantActions）、无图标；校验错误 12sp 红色配警告图标（2026-10-30 用户修正） |
| 分组标题 | SmallTitle 默认 14sp Bold | 15sp Bold、主题正文色 onSurface；浅色呈深色、深色反转（2026-10-29 用户修正） |
| 底栏标签 | 11sp，Medium/Bold | 保持 11sp，Normal/Bold |

- iOS 优先沿用系统解析器及现有回退链，核对中文系统回退、粗体开关、数字与英文；不打包 SF 私有字体文件，不指定不可移植的私有字体名绕过框架。
- Android 使用系统 sans-serif 与系统中文/符号回退，尊重厂商系统字体选择，不强制复制苹果字体。
- 不全局缩放 density/fontScale，不禁用用户字体放大；上述为默认缩放下的设计目标，大字时允许行高增长与值换行。
- 以同机原生系统字样作为对照；若系统解析结果正确，就只修字体层级与字重，而不是另造一套字体加载器。
- 2026-10-30 用户决定：iOS 继续使用系统 SF，按 Apple 各字号的字距与字重校正 Skia 排版差异；Android 使用系统字体。Apple SF 许可只允许制作 Apple 平台界面样稿且禁止再分发，Android 不打包 SF。
- 2026-10-30 调研（Compose 1.12.0 → Skiko 0.150.1 → Skia m150 内置 HarfBuzz）：HarfBuzz 只在字体同时含 trak 与 STAT 表时应用字距表，Skia 的 SkShaper 不设置 ptem，HarfBuzz 按默认 12pt 取值。SF Pro 的 Normal 字距在 12pt 为 0，所以 Compose 中西文字距不随字号变化，不会与手动字距叠加。苹方含 trak、无 STAT，Compose 不应用；原生 CoreText 是否对苹方应用 trak 未验证，所用苹方文件来源未核实。
- HIG iOS SF Pro 字距（pt）：11 +0.06，12 0，13 -0.08，14 -0.15，15 -0.23，16 -0.31，17 -0.43，18 -0.44，20 -0.45。项目文字为 11–18sp。苹方 trak 为 +0.019～+0.022 em（10–20pt）。西文与中文的偏差方向相反，统一设置 letterSpacing 会同时作用于中文（Skia 按字符簇加间距）。

**验收**：中文/英文/数字/符号无缺字，Normal/Bold 不假粗；深浅、大字体、系统粗体与长值不裁切，辅助信息不会与主设置名称同等突出。

### 3.7 选择菜单材质与弹出速度（原反馈 7）

**目标**：明暗模式、界面材质及其他单值选择菜单都读取同一个 materialMode，不另存“菜单材质”。减少透明效果时走实体回退。

- 菜单容器采用所选材质；背景景深另由宿主控制，不能把二者混成全屏同一效果。
- 入场立即响应、位移短、适度回弹；退场干脆，不套两层相互等待的动画。
- “动画看起来慢”先分为动画拖尾、点击回调延迟、实际掉帧三类；仅缩短时长不能修复后两类。
- 已核实高层 OverlayDropdownPreference 没有材质、进出动画或替换灰幕的参数，外壳还会绘制实色。若保留本轮三项诉求，拟在 ChoiceSetting 边界采用受控选择弹层，复用 Miuix 行/选项及公共低层 PopupLayout；不继续堆不存在的参数。详细默认值、宿主和接口边界见第 5.4 节。

**验收**：点击即开始反馈；打开/切选/关闭连贯；当前项、焦点、外部点击关闭、返回关闭、列表滚动和读屏保持；显示期间切主题/材质不出现旧色残留。

### 3.8 设置图标、值位置、即时编辑与镜像页（原反馈 8）

**2026-10-29 最新确认，优先于下文早期方案**：
- 设置页面内所有设置项配对应语义图标；卡片外分组标题采用第 3.6 节新规格。辅助说明不配图标，校验错误配警告图标，不单靠颜色表达；展开后的输入框左侧不重复设置行图标（2026-10-30 用户修正）。
- 原位展开项参照“点击震动”补展开/收起箭头与语义；内容高度及箭头使用弹簧，遵循减少动态效果。今后默认禁止淡入淡出，除非没有可选方案或有明确更优理由再讨论。
- 仅普通设置删除保存/取消，完成输入、失焦或收起时校验并自动保存；镜像新增/编辑弹窗继续手动保存。无效或保存失败时保留草稿，不把乐观值视为已保存。
- 设置首页品牌区改为紧凑横向结构，名称与版本清楚排列；子页当前页面说明重新设计为图标/页名与必要提示，不保留冗余三行简介或无用解释段落。
- 2026-10-30 用户要求重新设计并确认：首页为登机牌卡片（SettingsBoardingPass.kt）。上半部分为 52dp 品牌蓝 #346DF1 连续圆角图标（六边形 BrandMark 替换 Wave）、20sp Bold 名称、13sp 版本号（平台名由各端入口传入）、右侧粉色“赞助”胶囊，整块点击进入赞助页；中间虚线撕口与两侧半圆缺口；下半部分为口号。子页说明采用方案 A 大标题：去掉图标，30sp Bold 页名，下方 13sp 次要色提示，与分组标题左对齐。新增 SettingsPage.Sponsor 赞助占位页，赞助渠道和赞助人员数据接入前只显示页名。
- 恢复本页使用对应设置默认构造值；恢复全部使用完整 AppSettings()，包含默认镜像列表与选中状态。重置入口、卡片、图标、确认按钮按主题语义重新设计，执行前二次确认。
- 统一消息面向当前及后续应用功能；复用已有 SettingsSession/SettingsUiState 的保存事实，确认写入后显示成功，失败、输入错误、结果待确认分别表达。公共消息宿主尚未实施，待独立批准。
- 执行节奏：每次仅派一个代理，完成并验收后再经用户批准派下一个；设置 UI/自动保存/恢复默认任务代码与轻量检查已完成，尚未编译或真机验收；顶底栏及统一消息任务暂缓。当前证据见 [NOW](context/NOW.md)。

2026-10-21 镜像局部源码交付：[最新交互规格](design/mirror-settings/PRD.md)、[R2 线框](design/mirror-settings/index.html)。主列表、管理／编辑／删除弹窗及底部测试栏已接入源码；用户最新要求为普通拼接行无卡片、底部手动保存、HTTP(S)／裸域名输入、移除直连条目、空列表图标＋加粗提示无新增按钮。关闭镜像只直连；开启后镜像全部失败才直连。设置输入框增加 iOS 局部菜单抑制。结构／73项Python／diff检查通过；未构建／未真机验收。其余设置页面和第8项整体仍未完成。下文通用即时编辑建议不再适用于镜像新增／编辑，以局部规格为准。

**目标规则**：
1. 首页与子页的设置行增加语义一致的 leading icon，分组标题增加对应图标；优先复用已依赖的 Miuix 图标，装饰图标不重复读屏。
2. 已选值放在右侧、指示器左侧，不放在设置名称下方冒充辅助说明。超长值省略并提供完整编辑/选择入口，大字时允许自适应换行。
3. 单值选择用常驻上下双箭头；进入下级页用右箭头；真实展开区域用展开/收起指示和 expanded 语义；开关行保留 Switch。不能为了“统一”给所有行都加双箭头。
4. 必要说明与校验采用较小字号和对应提示/错误图标；删除无用解释段落，不为每个图标再加描述性文本。
5. 移除偏好编辑流程的“保存/取消”按钮，变更有效后直接应用并异步持久化。重置/删除等不可逆或破坏性操作仍要有明确保护，不能把去保存/取消理解成取消所有安全确认。

**已确认的布局缺口**：ChoiceSetting 的主题/材质选择已是右侧当前值＋常驻双箭头；SettingsActionRow 的导航值也已在右侧。真正把值放在标题下方的是 SettingsControls.kt:98–140 的文本/数值编辑行，且这些行缺展开指示、保留保存/取消。各类行及分组仍缺语义图标；镜像名称另用了 18sp，与其他设置行不一致。应按行类型修正，不重做已经正确的右侧值布局。

**即时编辑契约**：
- 枚举、开关、合法选择在单次语义动作内立即更新，继续复用 SettingsSession 的顺序事务及保存失败回滚。
- 数值、URL、正则、UA 等保留本地草稿；在 IME 完成、失焦或离开编辑区时校验完整输入，合法即自动提交，不需保存按钮。不能仅以“合法＋停顿若干毫秒”为 URL 的完成标志，例如 https://exa 可能已可解析却仍在输入中。
- 无效输入保留并就地提示，原有效配置继续使用；未完成、越界输入不进入 AppSettings、队列或磁盘。离页时无效稿不覆盖有效值；需保留的草稿属于编辑状态，不是业务设置。
- 镜像名称＋模式＋地址作为一个完整候选原子提交；复用现有 URL 校验及去重，空地址不插入列表，也不一边键入一边创建多个条目。
- 输入 owner 持有草稿与游标，不能因乐观值回流重建输入或关闭编辑。SettingsControls.kt:106–110 的 remember(value, editing) 和随 value 关闭 editing 的 effect，实施自动保存时必须同步调整。
- MirrorSettingsPage.kt:63–71 当前用 settings.mirrors 相等判断完成，但 SettingsSession.kt:58 已先发布乐观值，不能据此认定写盘成功。复用现有 Session/SettingsUiState 暴露必要的待确认/完成状态，不新建全局 Store；保存失败保留草稿并支持重试。写入后确认读取失败时显示结果待确认、阻止后续不安全写入，不误称已经回滚。
- 持久化失败处理、读失败重试及顺序事务继续复用现有链。若提供撤销，使用该字段的反向变换走同一校验事务，不整份恢复旧 AppSettings 覆盖其他新修改；异步探测/排序不得覆盖用户草稿或选择。

**GitHub 镜像页结构目标**：
- 顶部只保留紧凑返回动作。
- 设置组：启用镜像、当前镜像/模式；行高、左右边距、图标与其他子页一致。
- 镜像列表：每条“名称/域名＋单选状态＋真实测试结果”，次级结果文字有层级；每条保留一个清楚的管理入口，测试/编辑/移动/删除进入管理区，不混排大小不一的按钮。持久化选择标为“当前选择”，只有真实请求结果才能标实际使用路线。
- 添加/编辑：同一套紧凑字段、合法性即时校验和自动保存契约，不再切到一套完全不同风格的表单；条目只有合法后才进入列表。
- 探测：保留真实测试入口及取消在途测试的能力；“移除保存/取消”指表单按钮，不等于禁止用户停止网络任务。
- 保留已有手动选择优先、持久化排序、故障回退、URL 安全验证和网络取消，不以重设计为由重写探测核心。

**验收**：逐页盘点所有设置行/分组与数值位置；无同义不同尺寸控件；改值立即显示、重启恢复；非法/重复/越界输入不落盘；模拟保存失败、快速连续改值、返回和探测中离页均不丢已确认配置。

### 3.9 切页与弹簧运动（原反馈 9）

**已确认**：五个一级入口目前由根节点 when 直接替换，没有统一的页面进出动画；设置子页才使用 NavDisplay/MiuixDefault。固定版程序导航默认 500ms tween，手势 settle 默认 spring（阻尼比 1、刚度 146），项目沿用；NavDisplayEffects 的 dimAmount 默认 0.5，项目未覆盖。AppMotion 的 180ms 是主题/材质过渡时长，不是导航或菜单时长。现有拖拽胶囊的回弹不能代表整页已做运动设计。

**公开接入边界**：可通过 navGraphicsTransition、NavMotion 配置 spring；NavSettleSpec.Spring 使用小于 1 的阻尼比时必须设置 clampOvershoot=false，否则构造器拒绝。弹簧不承诺固定毫秒数，具体参数以设备表现校准。

**方案**：
- 一级切换采用短距离、轻淡入的紧凑过渡；不模拟层层压栈，不改变五入口的互斥关系。
- 子页采用短行程横向弹簧，返回沿同一路径反向，保留预测/边缘返回的进度、速度、取消和 RTL；不换掉既有导航状态机。
- 弹层、选中胶囊和按压释放采用有克制的回弹，不把所有整屏页面做大幅缩放。弹簧强度以响应/稳定时间和打断表现验收，不能仅凭增加 overshoot 参数声称更流畅。
- 所有自有动画必须服从应用减少动态效果与系统零倍率；正数系统倍率继续保留。官方组件内置动画的覆盖限制必须与应用适配范围一并核实。

**验收**：快速连续切页、入场中返回、手势取消、后台恢复不闪屏/丢位置/丢输入；受限运动模式无非必要弹跳；动画可以打断且不会锁住卡片点击直到长尾结束。

### 3.10 设置页卡片点击不灵与灰色反馈滞后（原反馈 10）

**用户已明确范围**：问题发生在设置页内的可点击卡片/设置项：点击后像没点中，过一会卡片才出现灰色按压反馈。不是底部悬浮胶囊导航栏的点击、拖动或选中动画。本项只检查设置首页与设置子页的可交互设置行，不扩展到其他页面的信息卡。

**现象与证据分开**：设置导航行已有真实 onClick；Miuix BasicComponent 本身未增加点击延时，但默认按压反馈 alpha 增量 0.10，进入/退出 spring response 分别为 0.2/0.35，释放会等待进入动画完成。选择行的 Dropdown holdDownState 还会保持至退场完成。这些是卡片灰色反馈时序的核查线索，不足以证明首次点击未命中的根因。需分别测量命中、滚动取消、回调执行与实际绘制。

**方案**：
- 以设置页已经静止、没有转场的状态为首个复现基线，分别测试选择行、子页跳转行、开关行与展开编辑行；记录触点落在名称、留白、右侧值和指示器时的命中结果。
- 关联按下、按压反馈状态、语义 onClick、设置/弹层/导航状态变化与下一帧，区分“回调没执行”与“操作已生效但灰色反馈迟到”。不把灰色覆盖出现当作点击成功的唯一证据。
- 检查设置卡片内单一点击所有权、父子命中冲突、LazyColumn 的滚动判定/按压延后及手势取消；名称、留白、值和箭头应属于同一行触区，内层开关等尾件不重复消费。
- 按下后及时给轻微可见反馈，正常点击在有效释放时执行，不等待反馈动画结束；滚动接管时取消按压和点击，不能为了提早反馈牺牲滑动防误触。
- 若设备确认默认 indication 时序需要替换，只在相关设置行局部提供 LocalIndication；BasicComponent 未公开反馈动画规格，不叠第二层 clickable，不将灰色覆盖范围扩大到无关设置项。
- 页面转场完成后立即点击设置卡片，作为单独场景检查 NavDisplay 的输入阻断恢复。第 3.9 节的 500ms 页面转场不能直接解释静止设置卡片的首次按下迟滞，更不能与底栏胶囊动画混为一谈。
- 当前设置页是共享 Compose UI，尚无证据指向 UIKit 互操作延时；不未经定位全局更改手势分发策略。

**验收**：在静止设置页及滚动停止后，轻点/快点均有及时反馈并且只执行一次；按下后滑动不误触，取消后无残留灰底；回调不依赖按压动画完成。结果以设置卡片录屏和事件/帧时间线为准，不用底栏交互测试代替。

**2026-10-30 实现（待真机确认）**：源码确认两段延迟。Compose 1.12.0 在可滚动容器内等待滚动判定后才发送 Press（iOS 等待 150ms，`Clickable.ios.kt`），快速轻点会在松手时补发 Press/Release；Miuix `MiuixIndication` 再叠加弹簧进入动画，松手时还要等进入动画完成。新增 `SettingsRowFeedback`，只用于可点击的设置行、选择行、展开行和开关行：收到 Press 立即显示 0.10 灰底，松手后 80ms 渐隐，滑动取消时立即撤销；下拉弹层内的选项仍用 Miuix 原有反馈。150ms 滚动判定保留，用于防止滑动误触。点击回调、震动和导航输入未改，没有找到首次点击丢失的源码证据。

### 3.11 全局点击震动（原反馈 11）

**需求解释**：“调整震动发现”按上下文理解为调整震动强度。新增全局点击反馈开关，强度轻/中/强，默认中；提案默认开关开启，已有用户首次读取按该默认初始化，不覆盖显式选择。

**方案**：
- 在现有外观/交互设置分组中加入“点击震动”开关与右侧强度选择；关闭时禁用强度操作或清晰表示其暂不生效，不丢用户强度偏好。
- 覆盖可交互控件的有效语义点击：导航、设置行、开关、按钮、菜单选项及列表动作。滑动、拖动、滚动惯性和手势取消不触发；禁用项与空白点击不触发。
- 不在根层监听所有 pointer down；那会误把滑动起手当点击，并与子控件重复。复用公共点击入口、统一平台适配，保留键盘/读屏激活的语义一致性。
- iOS 采用原生 impact 反馈的 light/medium/heavy 档位，实施时核对最低 iOS16 与当前 SDK 的可用/弃用调用；Android 以 TICK/CLICK/HEAVY_CLICK 等可用原生效果映射相对三档，并核对普通 VIBRATE 权限、触摸用途及系统触觉设置。Compose performHapticFeedback 只有类型、没有力度参数，不能把 ContextClick/LongPress 当轻/中/强。不承诺不同机型物理力度相同，不默认新增震动库。
- 已核实 Miuix OverlayDropdownPreference 打开时通过 LocalHapticFeedback 发出 ContextClick。为避免重复并让应用关闭真正生效，拟在应用 UI 根屏蔽库自动反馈，由语义点击入口统一调用双端适配；同时测试文本编辑等其他组件未被错误影响，系统键盘自带触觉不由应用接管。尊重系统策略与硬件能力，不读取 iOS 私有设置、不强制长震兜底。
- 底栏拖动结束也经过 onSelect/onDestination，因此不能在通用导航回调里加震动；接真实 tab 点击和键盘/读屏点击入口。顶栏只在按钮点击反馈，系统/预测/边缘返回不追加。滑块滑动、滚动取消、重组和存储回流均不震动。开启开关或改强度只反馈一次，不再叠加一次“预览”；关闭开关的这次点击也不震。
- 新偏好经 AppSettings/Codec/SettingsSession 持久化；缺失/坏类型只在内存回退，不写坏原始配置。最终 key 与枚举名在该阶段契约中固定并测试。

**验收**：开关关闭所有应用点击反馈停止；轻中强即时可试并重启恢复；每次有效点击至多一次；滑动零反馈；无振动硬件/系统限制无异常。

### 3.12 弹层背景由灰遮罩改为轻度模糊（原反馈 12）

**目标**：在菜单/弹窗后方对页面做轻度高斯景深模糊，前景菜单本身清晰并使用所选材质；背景仍不可交互，外部点击关闭等行为保持。

**方案**：
- 模糊作用于弹层下面的页面，不直接模糊含弹层的根节点，也不对菜单自身再次模糊。阴影、景深与材质分别控制，避免循环采样。
- 均匀实时 backdrop blur 拟以 6–10dp、noiseCoefficient=0 为轻量起点；这是本轮目标，不是 textureBlur 官方默认的 20dp/0.0045 噪点。仅弹层可见/过渡时启用一份背景效果，多个弹层不无限叠加；关闭后释放资源。不能在 onExpandedChange(false) 时立即撤掉模糊，因为它表示关闭开始而非退场完成。
- 移除多余灰色实色遮罩；必要的轻微明暗分离由可读性测试决定，不能把灰膜换一个名字保留下来。
- 已核实高层菜单没有背景 blur/scrim 槽位，单独改 Scaffold.popupHost 也不能消除列表内部灰幕。通过第 5.4 节的公共低层布局关闭内置 dim，由应用统一控制景深和输入屏障；不靠反射访问 internal，也不引入第二套路由/主题宿主。
- 系统/应用减少透明效果优先，采用清晰实体前景和适当的非模糊背景分离；不能以用户提出景深为由关闭无障碍降级。减少动态效果时不做逐帧模糊半径动画。

**验收**：前景文字锐利，背后页面轻微虚化且无重灰幕；开关弹层无全屏长帧、闪白或残影；焦点/读屏/返回/点击屏障与键盘层级正确。

## 4. 八项 Apple 设计原则复核

这部分用于检视当前截图与反馈，不宣称共享控件已成为 UIKit 原生控件。

| 原则 | 当前判断 | 修正落点 |
| --- | --- | --- |
| Purpose／目的 | 基本符合：五入口与设置分组明确 | 不扩大到新业务或额外展示页 |
| Agency／自主 | 违反：点击迟滞、表单实时化不完整 | 即时反馈、合法值自动保存、错误可恢复 |
| Responsibility／责任 | 本轮未涉及新权限/数据采集；保存保护仍适用 | 坏输入不落盘、震动遵循系统策略 |
| Familiarity／熟悉 | 违反：子页标题重复、系统栏不同步、材质割裂 | 纯图标返回、系统外观联动、统一浮层 |
| Flexibility／灵活 | 待验收：尚无大字/不同孔位/读屏完整证据 | 不删除安全区及字体缩放以换取紧凑 |
| Simplicity／简洁 | 违反：尺寸/说明层级与镜像表单不一致 | 紧凑字体、右侧值、图标、删除冗余按钮 |
| Craft／细节 | 违反：蒙灰、长按实化、反馈与帧耗问题 | 分层消融、录屏及性能复核 |
| Delight／愉悦 | 违反：当前用户感知不流畅 | 短促弹簧与可控触感，避免装饰性长尾 |

## 5. 组件、几何与接口核查

下面的对照用于实施前校准；公开接口不足的内容，不能通过猜测参数绕过。

### 5.1 平台与字体的已验证入口

| 对象 | 当前入口 | 实施边界 |
| --- | --- | --- |
| App 生效主题 | shared/src/commonMain/kotlin/com/ohmyfly/App.kt:92 | 统一向系统宿主发布生效外观，不另存主题状态 |
| 顶部 inset 与布局 | ui/DriftTopBar.kt:52–85 | 保留系统 inset，调整额外内距、视觉按钮与标题 |
| iOS Compose 宿主 | shared/src/iosMain/kotlin/com/ohmyfly/MainViewController.kt；iosApp/iosApp/ContentView.swift | 需补共享主题到真实 SwiftUI/UIKit 宿主的联动，避免仅改页面颜色 |
| Android 系统栏 | androidApp/src/main/kotlin/com/ohmyfly/app/MainActivity.kt:20–25 | 需核对手动主题下系统栏前景，继续保留边到边布局 |
| iOS 高刷许可 | iosApp/iosApp/Info.plist 的 CADisableMinimumFrameDurationOnPhone | 已启用，不把重复加开关写成性能优化 |
| 默认字体 | 固定 Compose PlatformFont.skiko.kt 的 GenericFontFamiliesMapping | 已有系统字体及回退，不额外分发 SF 字体资源 |

上述 ui/ 相对路径均位于 shared/src/commonMain/kotlin/com/ohmyfly/。

### 5.2 Melox 几何与当前值对照

这些是两个项目的实现值，不是 iOS 26 官方参数。Melox 默认四入口，当前五入口，不能把其单项宽度直接乘五照抄。

| 项目 | 当前 OhMyFly | 固定 Melox | 本轮目标 |
| --- | --- | --- | --- |
| 外轮廓 | GlassShape(24dp) 平滑方圆角 | CircleShape 胶囊 | 采用胶囊几何；裁剪/折射/高光/阴影一致 |
| 默认主体高度 | 最小 64dp、允许增长 | 固定 64dp | 首轮 64dp 基线、保留大字增长；不能声称照搬后高度会自动变小 |
| 选中胶囊 | 四边 inset 4dp，默认高 56dp | inset 4dp、高 56dp、CircleShape | 采用同一胶囊尺度与轮廓 |
| 图标 | 24dp | 22dp | 22dp |
| 标签/图文距离 | 11sp、间隔 4dp | 11sp、间隔 1dp | 11sp、间隔 1dp |
| 标签字重 | Medium/Bold | 全部 Normal | 按用户要求 Normal/Bold，不照搬全 Normal |
| 水平分配 | 五项等权；外左右 16dp、最大 560dp | 按文字测量，单项至少 100dp，受可用宽约束 | 五项等权，外左右拟 24dp、最大拟 420dp；极窄屏根据触区与完整标签调整 |

- 修改水平内 padding 不会缩短等权项的中心距，收拢必须实际减少外壳总宽。Melox 单项 100dp 的规则在五项时可能反而更宽，因此不照搬这一条。
- 定位：当前 ui/NavigationBarShape.kt:7、glass/GlassNavigationTabs.kt:69–83、GlassNavigationSurface.kt:91–128；Melox `app/src/main/java/com/melox/player/ui/component/liquid/LiquidGlassNavigationBar.kt` 的 203–223、248、418–442、521–524、566–609 行。
- 底部保留当前系统 inset＋8dp 的落点；不叠加 Melox 组件另一个默认 bottomPadding，避免安全区被算两次。

### 5.3 灰层、按压与性能的源码证据

| 材质 | 当前 blur（dp） | 表面覆盖 alpha（浅/深） | 当前特征 |
| --- | --- | --- | --- |
| 高斯模糊 | 20 | 0.22 / 0.28 | 仍有轻度折射和高光，并非纯 blur 基线 |
| 高光玻璃 | 4 | 0.16 / 0.22 | 边缘高光较强 |
| 柔光玻璃 | 24 | 0.30 / 0.36 | 模糊与混色更明显 |
| 液态玻璃 | 4 | 0.40 / 0.40 | 完整折射、饱和度与交互链 |

- 当前基底使用 surface；Melox 液态也有 0.4 覆盖，但用 surfaceContainer；其非液态甚至有 0.6 覆盖。照抄 Melox 的颜色参数不会自动消除蒙灰。
- 没有查到“长按后 alpha 直接改为 1”的代码。按下就开始白色 Plus 加亮、形变、色散、内阴影；胶囊最大缩放目标 78/56，静态覆盖反而随按压减少。应以“按压合成导致透景减弱”排查，不虚构实体化开关。
- 第一轮消融按顺序只改一个因素：surface 覆盖 → 按压 Plus 加亮 → 非液态隐藏录制 → Lens/色散 → Bloom/内阴影 → 冻结重力。实验开关不进入最终产品设置。
- 当前四模式都录制隐藏材质/图文，Melox 仅液态录制；边界 shader 在角区包含 6 次迭代，色散边缘最多 7 次采样。它们是性能候选，不是测得的耗时排名；shader 有缓存，不是每帧重新编译。
- 定位：glass/GlassPreset.kt:21–25、GlassEffects.kt:17–46、InteractiveHighlight.kt:54–76、GlassNavigationSurface.kt:79–172、GlassShape.kt:42–61、Lens.kt:122–175。iOS 传感器目前 60Hz 共享 manager，已有量化，不能描述成每次回调整页重组。

### 5.4 Miuix 弹层种类与本项目取舍

| 家族 | 可用于什么 | 本项目方向 |
| --- | --- | --- |
| Dropdown / OverlayListPopup | 锚定设置行的选项菜单/列表 | 明暗、材质等保持短菜单，不为了回弹改成完整页面 |
| Spinner 系列选择器 | 其他选择呈现；具体菜单/对话框模式由固定版参数决定 | 有明确场景再用，不混用不同选择风格 |
| OverlayDialog / WindowDialog | 确认、重要输入等模态内容 | 只用于必要确认，不恢复偏好的保存/取消步骤 |
| OverlayBottomSheet / WindowBottomSheet | 较长操作列表或较复杂编辑 | 镜像添加/编辑可选用，但必须遵循同一即时编辑契约 |

Overlay 使用现有 Scaffold 弹层宿主并随页面生命周期；Window 使用独立窗口，适合确有跨页需求的场景，不因模糊效果就全部换成 Window。

**固定 0.9.4 已核实接口**：

| 入口 | 可配置 | 不能直接完成的部分 |
| --- | --- | --- |
| OverlayDropdownPreference | dropdownColors、maxHeight、showValue、onExpandedChange 等 | 无弹窗背景/材质 modifier、动画 spec、enableWindowDim 或 blur/scrim 槽位 |
| OverlayListPopup | popupModifier、定位/关闭回调、enableWindowDim | 内部 ListPopupContent 仍画 surfaceContainer 实色、16dp 圆角；无完整材质槽位或动画 spec |
| OverlayDialog / OverlayBottomSheet | backgroundColor、modifier、enableWindowDim 等 | 高层无动画规格及背景 blur/scrim 槽位；不能据配色入口宣称整套材质已支持 |
| Scaffold.popupHost | 可替换宿主内容 | MiuixPopupHost 本身无统一材质/动画/模糊配置，宿主下方加 blur 不会删除内部 dim |
| 公共 MiuixPopupUtils.Companion.PopupLayout | enter/exitTransition、dimEnter/ExitTransition、enableWindowDim | 需应用承担锚点、越界避让、关闭、选项语义和退场生命周期，不是一行替换 |

**当前菜单默认时序**：缩放 spring 阻尼比 0.82、刚度 362.5、阈值 0.0001；内容 alpha 进入/退出 200/150ms；dim 进入/退出 300/150ms。项目沿用，ListPopupDefaults 是只读 val，高层没有 animationSpec 覆盖参数。

**首选适配**：只在现有 ChoiceSetting 边界替换为受控选择弹层，仍用 Miuix 行/选项、原选项 ID 和唯一 AppSettings 值；低层 PopupLayout 负责公共显示链，应用负责透明材质容器、短促运动、景深及完整 dismiss 生命周期。新实现验收后删除被替代菜单路径，不复制整库或访问 internal。

**采样边界**：根 Scaffold 已存在，无需再套一个；当前 App.kt:131–137 的 layerBackdrop 只捕获正文且受 glassEnabled 门控，顶/底栏在它外面。全屏景深需要明确覆盖页面及 chrome、排除当前弹层的采样/渲染边界，不能宣称现有正文 Backdrop 已含全部底层；Window 弹窗也不会自动受根层效果控制。

## 6. 验收与交付规则

| 类别 | 必须覆盖 |
| --- | --- |
| 视觉 | 四材质 × 浅/深；底栏静止/按压；菜单打开；子页返回与紧凑卡片前后截图 |
| 平台 | iOS 最低支持版本与高刷设备、Android API33 与高刷设备；孔位差异、横竖屏、键盘 |
| 外观 | 系统浅/深 × App 三种明暗选择；前后台、首启与弹层显示中切换 |
| 输入 | 单次/连续点击、按下后滑动、手势返回取消、动画中改选、键盘/读屏激活 |
| 设置 | 有效值即时应用、非法输入隔离、顺序保存/失败回滚、重启恢复、镜像探测取消 |
| 无障碍 | 减少动态/透明、系统震动策略、大字体/粗体、焦点、RTL 与文本对比 |
| 性能 | 冷/热启、首帧/可交互、滚动/菜单/返回/玻璃按压的帧耗与消融对照 |

- 本地检查验证结构、文档链接、配置与回归逻辑；Kotlin/Android/iOS 编译仍走既有 Actions，不在设备下载全套 SDK。
- 视觉与性能问题必须用更新后的真机截图/录屏/采样验收，不能只增加源码字符串守卫后宣称修好。
- 每阶段只保留一套生产实现；替换完成后清理旧分支、过时参数、无用状态与按钮引用，不长期并存两套菜单或表单。
- 每阶段同步实现笔记与原方案；尚未实施的阶段继续标为提案。推送、删历史构建记录、再次 Release 均需用户另行授权；本轮“先给文档”不包含这些动作。

## 7. 参考资料

- [原设置改造与逐项确认规则](settings-redesign.md)、[当前 UI 与设备验收范围](ui-showcase.md)、[工程和平台边界](engineering.md)。
- [Miuix 固定 0.9.4 源码](https://github.com/miuix-kotlin-multiplatform/miuix/tree/39c40f99844227b853f0049a0933b1f3ae6c00ba)；[默认 TextStyles](https://github.com/miuix-kotlin-multiplatform/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/TextStyles.kt)。
- 固定 Miuix 源码证据：[Preference 公开参数](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-preference/src/commonMain/kotlin/top/yukonga/miuix/kmp/preference/OverlayDropdownPreference.kt#L58-L76)、[菜单动画默认](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/ListPopup.kt#L198-L215)、[实色外壳](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/ListPopup.kt#L594-L624)、[公共 PopupLayout](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/utils/MiuixPopupUtils.kt#L249-L290)。
- 反馈与导航：[MiuixIndication 时序](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/utils/MiuixIndication.kt#L25-L90)、[导航默认时序](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/docs/guide/miuix-nav.md#L170-L199)、[Spring 约束](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-nav/src/commonMain/kotlin/top/yukonga/miuix/kmp/nav/transition/NavMotion.kt#L20-L45)。
- 自动保存现有能力：[SettingsSession](../shared/src/commonMain/kotlin/com/ohmyfly/settings/SettingsSession.kt)、[SettingsControls](../shared/src/commonMain/kotlin/com/ohmyfly/ui/settings/SettingsControls.kt)、[镜像编辑状态](../shared/src/commonMain/kotlin/com/ohmyfly/ui/settings/MirrorSettingsPage.kt)。
- [Compose 固定版字体映射/回退](https://github.com/JetBrains/compose-multiplatform-core/blob/f29d2f99f3beafc60992c6a73dbbaa9f841c5d4c/compose/ui/ui-text/src/skikoMain/kotlin/androidx/compose/ui/text/platform/PlatformFont.skiko.kt)、[平台文字差异](https://kotlinlang.org/docs/multiplatform/compose-platform-specifics.html#text)。
- [Apple 布局与安全区](https://developer.apple.com/design/human-interface-guidelines/layout)、[字体](https://developer.apple.com/design/human-interface-guidelines/typography)、[材质](https://developer.apple.com/design/human-interface-guidelines/materials)、[无障碍](https://developer.apple.com/design/human-interface-guidelines/accessibility)。
- [Apple preferredColorScheme](https://developer.apple.com/documentation/swiftui/view/preferredcolorscheme(_:))、[UIViewController 外观覆盖](https://developer.apple.com/documentation/uikit/uiviewcontroller/overrideuserinterfacestyle)、[safeAreaInsets](https://developer.apple.com/documentation/uikit/uiview/safeareainsets)。Apple API 页本轮提取仅取得页面元信息，具体宿主调用形态仍需实施时核实，未将标题当成完整接口验证。

## 8. 继续改进需求（2026-10-23）

来源：用户 2026-10-23 提出并确认细节，作为 D 阶段之后的继续优化。本节保留需求及确认时的状态，当前实施进度以 [NOW](context/NOW.md) 为准。U4 已于 2026-10-29 按用户要求暂缓，后续再议。

### 8.1 条目

| ID | 需求 | 期望 | 验收 | 状态 |
| --- | --- | --- | --- | --- |
| U1 | 弹窗与下拉动画 | 进入弹簧加大回弹、提高刚度以缩短进入；遮罩淡入缩短；退出用更短的缓动或弹簧。具体数值由实施方选定，要求人眼能明显感知回弹与更快进入 | 双端录屏对比前后进入时长与回弹，改动可被明显感知；连续开关、动画中关闭不残留遮罩；实施报告列出 Miuix 默认值与新值 | 已确认 |
| U2 | 全层级弹簧切页 | 一级、二级、三级及更深页面切换全部用弹簧；默认参数与 U1 同一组更有回弹的参数 | 各层级前进、返回、手势返回均为弹簧；减少动态时直接换页 | 已确认 |
| U3 | 镜像编辑器“拼接方式”对齐 | 见[镜像页规格](design/mirror-settings/PRD.md#弹窗与菜单) | AC-M12 | 已确认 |
| U4 | 统一 Sink 按压反馈 | 所有可点击区域使用统一 Sink 按压反馈；底部导航栏除外 | 逐页盘点可点击区域；底栏保持现状；与 [3.10](#310-设置页卡片点击不灵与灰色反馈滞后原反馈-10) 点击时序一并验证 | 暂缓；用户决定暂时不做，后续再议（2026-10-29） |
| U5 | App 名称 | 改为 OhMyFly | Android 启动器名、iOS 显示名、页面内品牌文字一致 | 已确认 |
| U6 | 包名 | Kotlin 包根 `dev.ohmydrift` → `com.ohmyfly`；Android applicationId 与 iOS bundle identifier 为 `com.ohmyfly.app`；shared 模块 namespace 由实施方按惯例选定（如 `com.ohmyfly.shared`） | 包引用、import、namespace、bundle id、测试、文档、CI 无旧值残留 | 已确认 |
| U7 | 订阅 → 塔台 | 入口名及所有页面内“订阅”文案替换 | 全文案检索无残留 | 已确认 |
| U8 | 节点库 → 航班 | 入口名及所有页面内“节点”文案替换 | 同上 | 已确认 |
| U9 | 测速 → 起飞 | 入口名及所有页面内“测速”文案替换 | 同上 | 已确认 |
| U10 | 配置后台异步、启动无加载界面 | 配置读取与持久化在后台；启动不显示“正在读取本地设置…”等文案或界面。读取失败时用弹窗提示，由图标、文字、按钮组合表达（如重试） | 冷启动直接进入主界面；模拟读取失败时出现弹窗且原文件不被覆盖；重试成功后弹窗关闭 | 代码完成（待构建） |
| U11 | 删除旧配置与兼容逻辑 | 不需要的、过时的、尚未实现功能的配置与代码全部删除，不保留：含 `MirrorMode.Template` 及“原模板”显示、未知 key 保留、`mirror.order` 遗留清理逻辑，以及其他同类项。设置页面内已有的子页与配置项（起飞、流媒体与 AI、塔台与过滤等）保留，不在删除范围（2026-10-24 用户确认） | 实施前列出删除清单；删除后无引用残留，测试同步更新 | 代码完成（待构建） |
| U12 | iOS 输入框浮动工具栏 | 双击或长按输入框时显示系统默认浮动工具栏（选择、全选、复制、粘贴等）；单击聚焦时不弹出工具栏 | iOS 真机：单击只聚焦；双击、长按出现工具栏且各项可用；Android 行为不变 | 已确认 |

### 8.2 现状事实（实施参考）

| 主题 | 事实 | 依据 |
| --- | --- | --- |
| 镜像页弹层 | 用 Overlay 系列（OverlayDialog、OverlayDropdownPopup、OverlayIconDropdownMenu），不是 Window 系列；Window 走平台 Dialog，不依赖 Scaffold | 用户确认 |
| 弹窗默认运动 | 手机底部滑入弹簧，阻尼 0.88 / 刚度 450；退出 tween 260ms；遮罩淡入 300ms、淡出 250ms | 用户确认，Miuix 0.9.4 |
| 下拉默认运动 | 展开比例弹簧，阻尼 0.82 / 刚度 362.5；内容淡入 200ms、淡出 150ms；遮罩 300ms / 150ms | 用户确认，Miuix 0.9.4 |
| 参数可控性 | 上述参数在 Miuix 0.9.4 封装层未暴露，需改内部实现或替换组件，与 [5.4](#54-miuix-弹层种类与本项目取舍) 的适配边界一并决定 | 用户确认 |
| “原模板” | 旧拼接模式 `MirrorMode.Template`（地址含 `{url}`），为兼容存量配置保留；U11 将删除 | 用户确认；`MirrorEditor.kt` |
| App.kt | Compose 根组件（233 行），不是系统入口；系统入口为 MainActivity / MainViewController。可整理，但不为凑行数拆包装层 | 用户确认 |
| 持久化 | UI 状态同步更新，磁盘写入异步（DataStore 协程事务）；镜像保存等待真实写入确认 | 用户确认 |
| 启动加载界面 | 设置未读出时显示品牌名与“正在读取本地设置…”，读失败显示“重试” | `PersistedSettings.kt` 第 41–51 行，2026-10-23 核对 |
| iOS 输入菜单 | 设置输入框通过 `LocalTextToolbar` 提供永远隐藏的 TextToolbar，所有途径都不显示工具栏；U12 需改为仅抑制单击聚焦时的弹出 | `SettingsInputMenu.ios.kt`，2026-10-23 核对 |
| Overscroll | MiuixTheme 自动提供 MiuixOverscrollFactory，设置页 LazyColumn 已继承；只在顶/底越界拖拽时可见 | 用户确认 |
| 改名波及面 | 所有旧 `dev.ohmydrift` 包引用与 import、Android applicationId/namespace、iOS bundle identifier、测试与文档；改名后旧本地数据不可见，不需保留 | 用户确认；`androidApp/build.gradle.kts`、`shared/build.gradle.kts`、`iosApp/Configuration/Config.xcconfig` |

### 8.3 待确认

无。实施方选定的数值与 namespace 在实施报告中列出，由用户真机验收确认。
