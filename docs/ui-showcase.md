# UI 展示界面

当前 Android 与 iOS 共用 `shared/src/commonMain` 的 Miuix 页面，没有新增原生业务页面。它是第一轮视觉与交互样板，不是已接入后端的产品。

## 入口与内容

顶部固定显示 **ohmydrift**，底部导航有三个目的地：

| 目的地 | 可以查看或操作的组件 |
|---|---|
| 概览 | SmallTitle 分组标题、BasicComponent 标准行、统计与操作分组卡、卡片尺寸过渡、顶栏背景模糊 |
| 节点 | 分类按钮、独立卡片列表、多选、当前分类全选、全局清空、已选数量 |
| 外观 | 跟随系统/浅色/深色、减少动态效果、色彩与文字层级 |

- 「展开动效」只展开说明，不启动任务；切换目的地有短淡入淡出。
- 「全选当前分类」只影响当前可见节点；其他分类的已选项保留。「清空所有选择」才清空全部。
- 外观、分类、已选项和三个列表位置在本次 App 会话内保留；没有数据库或设置持久化，重启后重置。
- 节点名称、协议和延迟全部是固定演示数据，行内标识「示例 / 非实测」。真实测速入口禁用，不执行网络请求。
- 「减少动态效果」关闭自定义页面淡入淡出、卡片尺寸过渡与本轮概览顶栏模糊，顶栏恢复不透明；Miuix 控件自身动效遵循其实现和系统设置，不声称全局无动画。
- 字体家族未显式指定，由 Compose 平台字体解析与回退决定，不承诺与 UIKit/SwiftUI 文本渲染一致；没有额外字体或图标包。已接入同版 miuix-blur，不增加 Material 页面或导航框架。

## 源码职责

- `App.kt`：主题、底部导航、会话内共享状态、滚动位置与页面切换。
- `ui/OverviewScreen.kt`、`ui/NodesScreen.kt`、`ui/AppearanceScreen.kt`：三个页面。
- `ui/DemoComponents.kt`：少量复用按钮和说明文字。
- `ui/DriftIcons.kt`：本地原创线条矢量图标。
- `ui/DemoNode.kt`：明确标识的演示数据和纯筛选/选择逻辑。
- `commonTest/.../DemoSelectionTest.kt`、`ThemeModeTest.kt`：分类、选择、空列表边界与主题模式测试。
- `ui/ThemeMode.kt`：命名的会话内主题模式，不依赖界面列表下标。

仅概览页改用锁定版官方 example 的分组列表惯例：卡片外侧12dp，SmallTitle 用默认28dp/8dp 内距；BasicComponent 的标题17sp Medium、说明14sp、内距16dp，Card 圆角使用默认16dp，不再使用自定义32sp品牌标题或大号统计卡。节点/外观页原排版保留，便于对照；这不是虚构的官方强制页面模板。

共享配色使用 ThemeController(MonetSystem, Expressive, Spec2025)，既有蓝色0xFF3482FF作种子以保证参数在两端生效。MonetSystem默认跟随系统明暗，不声称读取iOS壁纸；外观页仍可覆盖明暗。概览滚动内容使用 Scaffold 的边距作为 contentPadding，可滚入透明顶栏下方；rememberLayerBackdrop / layerBackdrop / textureBlur 使用上游官方25f顶栏模糊和0.8表面色覆盖，采样源先绘制不透明底色。

## 验收清单

完整构建按 [development.md](development.md) 交给 Actions，本地轻量检查不证明 Kotlin 编译成功。Release 工作流运行 `:shared:testAndroidHostTest`，以实际 JUnit 结果确认 commonTest 执行；不能把双端编译成功当成测试通过。

1. Android 和 iOS 启动后可见品牌和三个带文字标签的导航项；顶栏和底部导航不随内容滚走。
2. 各页滚动到底，内容不被导航、刘海或底部手势区覆盖；横屏和大字体仍能滚动查看，不出现水平内容裁切。
3. 选择欧洲节点，再切亚洲执行全选和取消全选，欧洲选择保持；全局清空后数量归零。
4. 三种外观模式立即作用于全部共享页面；切换页面后保留设置和列表位置。
5. 展开/收起品牌卡、切换导航，观察短过渡；启用减少动态效果后自定义过渡立即切换。
6. VoiceOver/TalkBack 可读导航标签、分类已选状态、多选名称与状态；装饰图标不重复播报按钮名称。
7. 检查系统字体放大、浅/深色对比度、iOS16 和 Android13 真机；不以最新模拟器替代最低系统验收。滚动概览检查顶栏模糊，打开减少动态效果检查不透明回退。
