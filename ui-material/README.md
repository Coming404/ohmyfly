# Miuix 材质 G2 适配

本模块保留 Miuix `0.9.5-beta03` 的公开包名、类型与模块身份，替换 `top.yukonga.miuix.kmp:miuix-material`，供官方玻璃组件及背景绘制共用。

## 源码与补丁

原版发布源码位于 `../third-party/miuix-material/`。Gradle 将归档中的 Android、common 与 Skiko 源集解包到 `build/generated/upstream`，不解包被替换的文件。

手工修改的官方源码统一使用 `Diy_` 前缀：

| 补丁 | 职责 |
| --- | --- |
| `Diy_GlassShape.kt` | 裁剪轮廓复用 `ui-shapes` 的 G2 几何；保持真圆与胶囊 |
| `Diy_GlassOverlays.kt` | 统一半径归一化、RTL、缩放和控制点 uniform |
| `agsl/Diy_MaterialAgsl.kt` | 用共享 G2 距离场替换原轮廓距离场 |
| `internal/Diy_EdgeStrokeShader.kt` | 边缘法线与同一距离场一致 |

带顶层函数的补丁保留原 `@file:JvmName`。`Diy_` 只是源码文件名，不改变类、函数、Klib 声明或 JVM 门面，避免预编译组件发生符号错配。

`G2Silhouette.kt` 与 `agsl/G2SilhouetteAgsl.kt` 是新增适配文件。前者读取实际 Path 的三次贝塞尔控制点，后者使用同一控制点计算距离，不另拟合一条外观相似曲线。尺寸及半径不变时复用几何；直线区域使用保守控制点边界跳过曲线迭代。

## 轮廓契约

- 每个连续角包含三段三次贝塞尔曲线；四角按右上、右下、左下、左上顺序。
- 半径归一化与 Compose `CornerBasedShape` 一致，再按共享几何的半短边约束处理。
- `smoothing == 0` 使用圆弧，大于零使用现有完整 G2 几何，不保留旧拟合曲线之间的插值。
- 真圆和胶囊保持解析圆弧。零半径角保持直角。
- shader 的控制点以实际像素传入，各采样级别通过同一 `scale` 换算。
- 折射、遮罩、阴影、高光均调用 `setSilhouetteUniforms` 和 `MaterialAgsl.SilhouetteSdf`，不得绕开这两个入口增加第二套圆角。

## 升级维护

更新 Miuix 时同时更新固定源码归档、版本目录和上述四个补丁。核对公开签名、JVM 门面、各 shader 的 uniform 契约及 Android/iOS 实际编译链接。不要同时依赖发布版和本地版材质模块。

## 鸣谢

- [Miuix v0.9.5-beta03](https://github.com/compose-miuix-ui/miuix/tree/3526cfe2ca0779c5a2eab99af8737a96c9a8a885)：官方材质、shader、阴影和光照实现。Copyright 2026, compose-miuix-ui contributors。Apache-2.0，许可见 [保留文件](../third-party/licenses/glass-Apache-2.0.txt)。修改内容列于上表；归档保留上游原始署名。
- [Kyant Shapes 1.2.1](https://github.com/Kyant0/Shapes/tree/1.2.1)：通过 `ui-shapes` 复用连续曲率几何。Apache-2.0。项目不再依赖 Kyant Backdrop 玻璃引擎。
