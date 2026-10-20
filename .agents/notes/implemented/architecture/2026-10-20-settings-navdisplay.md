# Agent Note: 设置子页接入 Miuix NavDisplay

Status: implemented

## Problem

用户明确授权接入NavDisplay；五个一级入口继续使用iOS方向的悬浮底栏，底栏外观/液态玻璃材质不是本项。现有设置页由单个SettingsPage值与when分发，App另装平台SettingsBackHandler；没有页面返回栈与官方转场/手势。镜像页内部编辑表单仍须先关闭草稿，不能让新返回手势直接退页。

## Decision

- 复用已有miuix-nav 0.9.4。保留SettingsPage七个枚举值，增加@Serializable与NavKey；单个entry<SettingsPage>按枚举值分发，因为它们属于同一具体运行时类型，每个枚举值的相等性/名称稳定且唯一。
- App用rememberNavBackStack<SettingsPage>(Home)替代独立settingsPage状态。顶部标题、返回与底栏显隐只从栈派生；一级目的地仍是互斥切换，不随点击入栈，仅按稳定name经rememberSaveable保存/恢复，避免序列化ImageVector。只从Home打开一个业务子页，重复点击不入栈，返回不弹空根页。
- SettingsScreen承载NavDisplay，正常运动显式采用官方MiuixDefault转场；子页选择LTR/RTL对应的物理滑动方向，根页不可滑退。[第7项运动策略](../../implemented/feature/2026-10-20-reduced-motion.md)在有效减少动态时改None与effects None并禁用空间滑退，不重建栈或编辑页。当前宿主最大760dp，关闭屏幕圆角效果，不把物理屏幕圆角误用到内容窗格；阻断过渡中非稳态页面输入。页面实体背景来自现有共享页面色，仍在原Scaffold/Backdrop范围内，不嵌套Scaffold或复制业务状态。
- 镜像编辑Open状态仍由原页面持有。只向App报告一个可空的本地返回动作，标题返回先关编辑器；编辑时禁用该页滑退。系统返回使用NavDisplay同一公共PredictiveBackHandler事件链，完成后关闭编辑器，取消不丢稿；删除旧expect/actual返回适配，避免两个事件体系竞争。
- 启用与Kotlin同版本2.4.20的serialization插件，直接声明与Miuix上游一致的serialization-json1.11.0，因为上游仅implementation依赖不保证本项目可编译注解与内联serializer。沿用库Saver，不自己写栈编码器或持久化配置字段。

## Existing decisions audit

- [设置首页](../../implemented/feature/2026-10-19-settings-home.md)/[预设选择](../../implemented/feature/2026-10-19-preset-selection-popup.md)与[紧凑顶栏](../../implemented/feature/2026-10-19-compact-page-header.md)决定部分重叠：六子页入口、纯图标返回、唯一配置及弹层宿主保留；单值设置路由/平台返回实现由本项替代。
- [五入口悬浮底栏](../../implemented/feature/2026-10-20-stacked-bottom-navigation.md)及材质不改；本项不把普通NavigationBar当作悬浮导航替代，不处理第6液态玻璃材质和第7整体动画/平台减少动态效果。
- [镜像配置](../../implemented/feature/2026-10-19-settings-mirrors.md)/校验/保存后关闭与探测作用域保持，不将编辑稿/网络任务放进序列化路由。

## Alternatives considered

- 七个sealed data object最贴近官方示例，可分别注册路由，但当前枚举本身可实现NavKey/生成serializer且全是无参数页面，保留枚举更小、调用方不用全改；未来确有携带参数的目的地再改。
- 五个入口全部push到一条栈可直接套NavDisplay，但会制造重复一级页面与错误返回历史，不采用。
- 仅navBackStackOf内存栈不需要序列化插件，但丢失配置变更后的路由恢复能力，优先使用库自带可保存栈。
- 手写Crossfade/返回手势比引入运行时简单，但现有依赖已具备完整能力且用户明确要接入，删除旧逻辑而非双轨并行。

## Consequences

- 新增SettingsNavigationTest六项覆盖六子页开页、防重复/过期点击、Home不重复、pop保根、全部枚举serializer回环与恢复后继续导航。当前74项共享测试仅声明未运行，CI最低数量同步74；新增5项Python接线守卫，迁移原顶栏守卫，累计49项通过，结构及diff检查通过。这些源码守卫不是Compose语义/手势或Kotlin编译证明。
- 删除旧commonMain/androidMain/iosMain SettingsBackHandler三文件，移除共享模块只供旧返回实现使用的activity-compose直接依赖；Android宿主仍需该依赖，不误删。没有旧单值路由、第二返回栈、私有导航API、手写页面转场或自定义手势。SettingsPage本身承载两项小型栈操作，不增加控制器包装层。
- 先用Skill路由契约，再用EXA核实固定commit的NavDisplay公开签名/枚举具体类注册、NavBackStack Saver、NavDisplayEffects与PredictiveBackHandler返回分发。子代理未形成可用结论，未把它作为审查通过；主代理直接补核公开源码和最小参数。来源入口在[资料](../../../../docs/references.md)。
- Klibs已核对Kotlin官方serialization-json：最新稳定1.11.0，预览1.12.0-RC不使用；项目为JVM1.8与双iOS目标，AndroidJvm过滤不命中后查官方README确认Android的JVM制品支持，不伪造专门AndroidJvm目标。官方Apache-2.0及上游Miuix锁定1.11.0/项目Kotlin2.4.20已核实，精确最低系统/最终解析树仍待构建。只为真实路由Saver补序列化依赖，DataStore格式及密钥/凭据不变。
- 本轮没有提交、推送、触发Actions、下载包或改签名；所有本地已存在的视觉改动保留。第6材质/折射和第7平台减少动态效果/整体动画不因此标为完成。

## Verification limits

官方转场/预测返回实现不等于本项目已真机验收；弹层优先级、输入法、镜像草稿和网络取消、大字/RTL/双端iOS16/API33需设备验证。镜像探测作用域在entry退出动画结束清理时取消，不声称点击返回瞬间就停止所有底层IO。路由Saver只恢复导航身份，不保存编辑稿/节点凭据，也不是任意冷启动记住上次页面。顶部与底栏当前在根Scaffold随逻辑栈更新，不新增跨层逐帧动画状态机；该联动表现留后续验收。
