# 统一连续曲率

`ui-shapes` 是应用与 Miuix 0.9.5-beta03 共用的圆角层。非胶囊表面委托 Kyant Shapes 1.2.1 的 `RoundedRectangle(Continuous)` / `UnevenRoundedRectangle(Continuous)`，曲线采用 G2 连续曲率。正圆按钮、胶囊标签和导航栏保留真圆／真胶囊。

## 唯一配置入口

[`AppCorners.kt`](src/commonMain/kotlin/com/ohmyfly/shapes/AppCorners.kt) 管理应用半径和形状：

| 角色 | 基准半径 |
| --- | --- |
| 主卡片 | 24 dp |
| 设置卡片、普通按钮、输入框、通知 | 16 dp |
| 弹窗、抽屉 | 28 dp |
| 选择操作 | 18 dp |
| 内容块 | 12 dp |
| 紧凑控件 | 8 dp |
| 骨架文字 | 4 dp |

`shape(radius)` 返回共享的连续曲率轮廓；半径达到半短边时使用真胶囊。`tile(size)` 按图标底座尺寸计算半径。嵌套表面用 `inset(outer, spacing)` 计算内层半径，避免在调用处另写一套数值。

背景、裁剪、选择覆盖和描边必须使用同一半径。44 dp 点击范围、字号和布局间距继续由原控件负责，圆角层不改变点击行为。

## Miuix 接入

根构建将 `top.yukonga.miuix.kmp:miuix-squircle` 统一替换为本地 `:miuix-squircle` 项目，目录映射到 `ui-shapes`。保留原模块 group、项目名、公开函数签名与 JVM 文件门面名；源码文件使用 `Diy_` 前缀，通过 `@file:JvmName` 保留原门面，覆盖组件内部的填充、裁剪、路径描边和按压反馈，避免在外侧再叠一层不同曲线。

固定版本的 `extension` 参数仍保留在接口中，实际曲率由 G2 几何决定；它不再拉伸名义半径。方向式四角随 RTL 翻转，绝对位置式四角保持物理方向。显式关闭库圆角开关时遵循原接口的圆弧分支。

## 玻璃表面

应用的玻璃顶部、底栏和独立动作保留真圆／真胶囊；菜单、对话框和抽屉通过 [ui-material](../ui-material/README.md) 的官方源码补丁复用本层 G2 路径。折射、高光、阴影和遮罩从实际轮廓提取同一份控制点，普通控件仍走本模块的填充、裁剪与描边。

## 版本与维护

- `io.github.kyant0:shapes:1.2.1` 作为独立几何依赖保留；Kyant Backdrop 已移除。
- Android、iosArm64、iosSimulatorArm64 跟随主项目目标与最低版本；设备单端归档遵循 `iosDeviceOnly`。
- 升级 Miuix 时核对其 squircle 公开签名及平台元数据；升级 Shapes 时核对连续曲率轮廓及胶囊饱和行为。
- Apple 提供 `.continuous` 与同心圆角公开能力；本实现复用跨平台 G2 曲线，匹配其设计风格，不声称使用 Apple 私有控制点。

## 鸣谢

- [Kyant Shapes 1.2.1](https://github.com/Kyant0/Shapes/tree/1.2.1)：连续曲率几何，[Apache-2.0](https://github.com/Kyant0/Shapes/blob/1.2.1/LICENSE)。
- [Miuix 0.9.5-beta03](https://github.com/compose-miuix-ui/miuix/tree/3526cfe2ca0779c5a2eab99af8737a96c9a8a885)：组件公开接口与描边内缩约定，Copyright 2026, compose-miuix-ui contributors，Apache-2.0；许可文本见[保留文件](../third-party/licenses/glass-Apache-2.0.txt)。
- [Apple RoundedCornerStyle.continuous](https://developer.apple.com/documentation/swiftui/roundedcornerstyle/continuous)、[ConcentricRectangle](https://developer.apple.com/documentation/swiftui/concentricrectangle)：连续曲率及同心布局设计依据。
