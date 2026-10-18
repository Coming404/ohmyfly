# UI 实现与验收

Android与iOS共用 `shared/src/commonMain` 的Miuix界面，保留五个入口：概览、订阅、测速、节点库、设置。生产源码不包含固定假节点、固定延迟、开发状态页脚或占位文件名。当前目录初始为空；订阅读取、安全凭据存储、Go节点测速尚未接入。设置已接DataStore与独立镜像HTTP探测，尚未编译及真机验收，不代表节点／媒体业务完成。

## 页面与状态

| 页面 | 当前内容 |
|---|---|
| 概览 | 目录数量、已选数量、最近结果空状态、四个功能入口 |
| 订阅 | 正式订阅空状态；不读取文件或请求网络 |
| 测速 | 选中节点范围、待测列表；引擎接通前开始按钮禁用 |
| 节点库 | 节点摘要、地区分类、多选、当前分类全选及全局清空；没有节点时显示空状态 |
| 设置 | 分组子页、真实设置持久化、GitHub镜像管理与独立HTTP探测；[详细边界](settings.md) |

选择与五个滚动位置由App持有。节点摘要通过参数下传，未知延迟为空并显示“未测速”，不得用固定数值填充。选择夹具只存在于commonTest，不参与应用运行时。

## 悬浮控制层与材质

- 顶部品牌在安全区内靠起始边悬浮，不是全宽不透明标题条；页面动作保留48dp触区。
- 底部使用Miuix FloatingNavigationBar，外层透明；保留上游安全区处理，不重复叠加底部系统边距。
- 五页列表视口占满内容层，Scaffold边距加入LazyColumn.contentPadding，只给首末项留白；不得用外层padding截断视口，否则底栏后方只剩纯背景。
- 单一LayerBackdrop只录制页面内容，不包含顶底控制层；两个小胶囊分别采样自身后方的内容。只共享捕获源，不声称两栏共用一次模糊结果。

顶底均由 `ChromeMaterial.kt` 使用同一配置：

| 项目设置（不是Apple规范） | 值 |
|---|---|
| textureBlur半径 | 20f，库按dp转换 |
| 表面色覆盖alpha | 浅色0.22、深色0.28 |
| 噪声系数 | 0f |
| 圆角参数 | 50dp |
| 阴影 | 半径8dp、黑色alpha0.12 |
| 边界线 | 0.5dp、onSurface alpha0.10 |

textureBlur由库内部自适应降采样，没有臆造downsampleFactor参数；不使用渐进全屏模糊或复杂折射shader。启用减少透明效果时，两栏共同使用不透明表面色，并停止页面采样。该材质是共享界面的视觉近似，不等于iOS26原生Liquid Glass。

## 配色

- Android宿主启用MonetSystem，keyColor=null，不传固定蓝种子、Vibrant或Spec2025，遵循系统动态色。
- iOS宿主明确使用System与Miuix默认浅深色回退，不声称读取壁纸，也不使用固定紫色种子伪装系统动态色。
- System只选择明暗，lightColors/darkColors仍可自定义；当前未新增用户自定义色板功能。
- 默认字体由Compose平台解析，不增加字体包，不承诺与UIKit/SwiftUI渲染完全一致。

## 性能与源码职责

App只绘制当前页面，不使用全局双页Crossfade，已删除展开体验和直接animation声明；不读取滚动偏移触发整页重组。Miuix自身动画仍遵循上游实现，不盲目关闭ProMotion或限制系统字体缩放。

- `App.kt`：主题、单一采样源与页面切换；`PersistedSettings.kt`统一加载与保存错误处理，`settings/`持有模型和DataStore；`mirror/`负责有界公开资源探测。
- `ui/{DriftTopBar,DriftNavigationBar,ChromeMaterial}.kt`：悬浮控制层与共享材质。
- `ui/PagePadding.kt`：逻辑方向安全区和内容留白合并。
- `ui/{NodeSummary,NodeSelection,NodeSelectionActions,ChoiceOption}.kt`：节点摘要、选择逻辑和控件。
- `ui/{OverviewScreen,SubscriptionsScreen,SpeedScreen,NodesScreen,SettingsScreen}.kt`：五页。
- `commonTest`：NodeSelectionTest、PagePaddingTest、AppDestinationTest、ThemeModeTest；真实测试保留标准命名。

## 验收清单

本地结构与Python测试不是Kotlin编译器；完整构建按[开发流程](development.md)交给Actions。当前修订未做真机帧耗测量，不报告性能改善百分比。

1. 五页切换时品牌靠安全区左上，底栏没有全宽白底；静止首末项可见，滚动内容能从两个胶囊后方经过。
2. 浅/深色及减少透明效果下，顶底参数与回退保持一致，无黑白闪烁或残留旧主题采样。
3. 横屏、刘海/手势区、RTL与大字体下按钮仍可触达，品牌文字必要时省略但读屏名称完整。
4. 空目录不显示假延迟；节点后端接通前不能启动节点测速。镜像探测独立实现且只在用户主动点击时联网，不把镜像结果当节点结果。
5. VoiceOver/TalkBack正确播报目的地、选择状态和操作名称，装饰图标不重复播报。
6. 最低iOS16/Android13真机检查；用Instruments/帧时间对比滚动与切页，不把静态审查当成卡顿根因或GPU性能证据。
