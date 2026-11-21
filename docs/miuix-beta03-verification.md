# Miuix beta03 与 G2 验证记录

## 迁移范围

- Miuix 0.9.5-beta03，Kotlin2.4.21、Compose1.13.0-rc01、AGP9.5.2、Gradle9.9.2、serialization1.13.0。
- 删除 Kyant Backdrop 和旧四档材质，保留独立 Shapes1.2.1 几何；风格为液态玻璃／Expressive。
- 官方 GlassNavigationBar、glassPanel、glassDialogSurface、glassBottomSheetSurface；菜单使用发布版确实提供 surface 重载的 OverlayIconCascadingDropdownMenu 与 OverlayCascadingListPopup。
- Glass 图标替换 UI 动作；应用、平台和地区品牌资源保留。
- 本地 :miuix-material 与 :miuix-squircle 全依赖图替换；官方修改文件均以 Diy_ 标记，顶层函数通过 JvmName 保持原门面。

## 几何复核

独立 Python 数值计算使用固定 Shapes1.2.1 控制点公式，核对导数和曲率；光学实现直接读取实际 Compose Path 控制点，不复制该数值脚本公式进产品。

| 检查 | 已执行结果 |
| --- | --- |
| 81组对称／非对称转角 | 最大单位切线叉积4.05e-16，曲率连接差3.11e-15，直线接入处曲率0 |
| 2430个距离样本 | 对照64点定位＋40轮细化参考，最大距离差3.53e-7 px |
| 4860个完整轮廓样本 | 四角排序、RTL、18组不等半径/受限尺寸，所选四分区与全部角参考的边界距离差0 |
| 270个内外偏移样本 | 符号全部一致，0.5px偏移最大距离差2.63e-13px |

完整轮廓复核使用float32控制点，覆盖density/scale为1/1、2/1、3/2。它验证几何与光学距离计算，不是Android AGSL/iOS SkSL真实GPU执行结果。

复核期间发现并修正：光学uniform原先只限制单角，未同步Compose CornerBasedShape相邻角归一化；现在布局裁剪与光学参数采用相同顺序。保留真圆和胶囊的解析圆弧路径；稳定几何缓存不按帧创建列表；转角计算使用保守控制点凸包裁剪。

## 工程检查

- beta03发布metadata已核对Android、iosArm64、iosSimulatorArm64；两端预编译glass依赖公共miuix-material坐标，因此命中项目替换。
- squircle13个公开函数与beta03匹配；原模块身份和4个JVM门面保留。
- 现有Python接线检查91项通过（4.211s）；项目结构、文档链接及本轮限定差异格式检查通过。旧引擎专用断言随实现删除，设置读写、路由、无障碍与主题检查保留。
- 当前尚未运行Kotlin/Gradle、Android/iOS云构建或真机shader与帧时间检查。新增源码生成模块、iOS Klib链接、AGSL/SkSL编译及实际性能由后续双端构建/设备验收确认。

## 后续设备核验重点

1. iOS16与当前iOS、Android API33设备的菜单/抽屉/底栏玻璃和减少透明效果。
2. 窄屏、大字体、RTL、全圆胶囊与非对称抽屉轮廓。
3. 折射边界、细高光和阴影在滚动及形变中是否贴合，检查GPU帧时间。
4. Expressive两端配色、旧设置读取为新默认值、正常写入只保存新枚举。

本轮没有提交或推送，也没有触发云构建。
