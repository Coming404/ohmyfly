# Agent Note: 6.3 玻璃倾斜重力双端传感器

Status: implemented

## Problem

Melox 用 Miuix 0.9.4 的 DeviceTilt.gravityX/Y 驱动边缘光；其 Android 值是固定设备坐标，未随 Display.rotation 映射，非 Android 实现恒为 Zero。6.3 需要 iOS 真正采集且双端只在启用、前台和可见 composition 内运行。渲染与业务开关由其他写入者负责。

## Decision

- 保留 `dev.ohmydrift.ui.glass.sensor` 的 `@Composable internal expect fun rememberGlassGravity(enabled: Boolean): State<Offset>`：x 向屏幕右、y 向屏幕上，单位重力的平面投影，不把 xy 再归一化到单位圆；模长不大于 0.1 返回 Zero。平方阈值也用 `0.1f * 0.1f`，避免 Float 边界把恰好 0.1 当成非平躺。
- Android 只在 enabled 且 Lifecycle.RESUMED 时条件调用公开 rememberDeviceTilt，沿用其 0.15 低通与首次样本直达，不进行第二次滤波。用 DisplayManager.DisplayListener 维护 Display.rotation，再投影成屏幕坐标；离开活动分支即 dispose Miuix 的采集和 lifecycle observer，Miuix 自身也在 ON_STOP 同步注销传感器。
- iOS 只采集 CoreMotion.CMMotionManager / CMDeviceMotion.gravity，一个惰性 app-wide manager 和最小订阅表服务所有玻璃组件；最后一个订阅移除即 stop。60 Hz 主队列回调，在设备坐标中做一次 0.15 低通，首次/重启样本直达；generation 拒绝停止后排队的旧回调。无硬件保持 Zero，错误停止并将当前订阅归零，不自动循环重试。
- iOS 用 Compose Lifecycle.RESUMED、UIApplication.applicationState、宿主 UIViewController.view.window.windowScene.activationState 和系统 Reduce Motion 共同限制采集。UIApplication/UIScene 失活通知立即停止并移除本地 UIKit observers；后台只保留一个已有 Lifecycle 的恢复 observer，ON_RESUME 重建 native observers。false 或离开 composition 同时移除该恢复 observer；无新设置或依赖。
- iOS 每次样本读取宿主 windowScene.interfaceOrientation，不使用 UIDevice.orientation。portrait/upsideDown/landscapeRight/landscapeLeft 映射为 Display.rotation 约定的 0/2/1/3。初次 unknown 用 -1 得到静态 Zero；已有可靠方向后 unknown 保持最后记录的映射，不猜 portrait。旋转发生在设备坐标滤波之后，不跨界面方向插值。
- enabled 仍由渲染方表达可见性/减少动态偏好；[第7项运动策略](2026-10-20-reduced-motion.md)统一合并应用reduceMotion与平台MotionDurationScale后传入，不新增采集器。Info.plist 的写入留给主代理，见下方权限缺口。

## Evidence

- [Miuix 0.9.4 Android DeviceTilt（锁定提交）](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-blur/src/androidMain/kotlin/top/yukonga/miuix/kmp/blur/sensor/DeviceTilt.android.kt)：公开 rememberDeviceTilt、设备坐标 gravityX/Y、首次样本、smoothing、ON_START/ON_STOP，以及没有 Display.rotation 修正均经源码核实。
- [Miuix 同版公共契约](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/miuix-blur/src/commonMain/kotlin/top/yukonga/miuix/kmp/blur/sensor/DeviceTilt.kt)：默认 smoothing 为 0.15、无传感器为 Zero、非 Android 返回 Zero。
- [Apple CMMotionManager](https://developer.apple.com/documentation/coremotion/cmmotionmanager)：要求 app 只创建一个 manager；不再需要数据时调用 stop；通过 OperationQueue 回调、deviceMotion.gravity 和可用性检查实现此范围。
- [Apple interfaceOrientation](https://developer.apple.com/documentation/uikit/uiwindowscene/interfaceorientation)：是窗口内容方向，旋转锁下可与 device orientation 不同；iOS13 起可用，iOS26 才弃用，符合最低 iOS16。
- [Apple UIScene willDeactivateNotification](https://developer.apple.com/documentation/uikit/uiscene/willdeactivatenotification) 与 [UIApplication willResignActiveNotification](https://developer.apple.com/documentation/uikit/uiapplication/willresignactivenotification)：使用官方失活通知而非不存在的屏幕旋转通知；scene notification.object 指明所属 scene。
- [Apple NSMotionUsageDescription](https://developer.apple.com/documentation/bundleresources/information-property-list/nsmotionusagedescription)：官方广义措辞为访问 motion data 时 required，未单独承诺 CMMotionManager 实时 gravity 可豁免。已向主代理说明建议添加用途说明；本 owner 不修改 Info.plist，不把权限配置声称为已完成。
- 查证顺序：先复用上游调查结论，精确 API 缺口用 EXA；Apple 动态文档 EXA 只返回标题时才用浏览器读取官方页面/DocC 内容。未用 curl/wget。

## Existing decisions audit

- [材质选择](2026-10-20-material-selection.md)：部分重叠，只落地其中后续6.3的传感器，不将材质渲染标记为已完成。
- [底部导航](2026-10-20-stacked-bottom-navigation.md) / [浮动 chrome](../bug-fix/2026-10-19-floating-chrome.md)：保留布局与减少透明效果决定，enabled 的业务决定留给渲染方。
- 未改变其他写入者的笔记或主文档。

## Alternatives considered

- 非 Android 直接用 Miuix：依赖最少，但锁定版明确返回 Zero，无法满足 iOS。
- 双端重写原生采集：能统一实现细节，但 Android 已有公开传感器与生命周期逻辑，不复制 SensorManager。
- 每个玻璃组件各建 CMMotionManager：局部代码简单，但 Apple 明确要求 app 单一 manager；采用最小共享订阅表避免顶部/底部同时采样和恢复累积。
- 将 unknown 直接视为 portrait：行为连续、少一个状态，但启动/横屏方向未知时会出现无依据的光源；初次返回 Zero、后续保留已知映射更安全。

## Consequences

- 实现文件均小于 300 行，API 与渲染读 draw-phase State 的方式不变；iOS 多处玻璃共享一次采样和一次滤波。
- `GlassGravityTest.kt` 声明 **19 个 @Test**：四方向基轴、同一屏幕重力的四种物理方向、投影模长、包含边界的平躺阈值、超界 clamp、unknown/非有限值/overflow、首样本与重启、0.15 低通、滤波后旋转与收敛。按授权未运行 Kotlin/Gradle 测试，不将声明数当成通过数。
- 差异空白检查与文件行数/@Test 数已作轻量检查；Python 全局门禁由主代理处理。未执行编译、SDK 安装、Gradle、CI、commit 或 push。
- **待主代理接续：**依据上述 Apple 权限文档补 Info.plist 的 NSMotionUsageDescription，建议文案“用于随设备倾斜调整玻璃高光，不记录或上传运动数据。”本改动未配置该键。
- Kotlin/Native 互操作签名、iOS16 真机生命周期/多 scene 恢复、屏幕旋转与无硬件/拒绝/错误回退，仍需后续授权的 macOS 编译与真机验收；本地静态检查不替代这些运行证据。
