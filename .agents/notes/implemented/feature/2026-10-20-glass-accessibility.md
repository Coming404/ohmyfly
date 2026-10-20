# Agent Note: 6.4 玻璃无障碍与实体降级

Status: implemented

## Problem

6.3 已接玻璃效果，但根页面只读取应用内减少透明效果，iOS 系统同名偏好未进入共享能力门控。底栏关闭默认 indication 后缺少可见键盘焦点；设置编辑错误未关联输入框，操作按钮普通 Row 在大字体/窄宽度下不能换行。

## Decision

- 沿用唯一 AppSettings、Scaffold 和玻璃效果链。`ui/SystemTransparency` 的最小 expect/actual 读取系统减少透明效果：iOS 监听 UIKit 变更通知并在回到前台时刷新，注册后再读一次，onDispose 清理观察者；Android 维持应用内开关。系统状态不写入 DataStore。
- `glassEffectsEnabled` 合并应用/系统偏好与 shader 支持，任一透明限制走原实体路径，同时停止页面采样、隐藏图文录制和倾斜追光；四项材质选择保持，恢复后重新显示所选效果。
- 真实 Tab 在标准 selectable 前观察焦点。局部 focusedIndex 只画轮廓，不触发导航；2dp onSurface/12dp GlassShape 轮廓在胶囊之后绘制，防止 Lens 覆盖。轮廓和隐藏图文副本都清除语义，无输入和重复 focusable。尺寸为本应用值，不是 Apple/Miuix 默认。
- `TextSetting` 的字段使用 error 语义，原有可见校验消息用 polite liveRegion；保存禁用及原校验不变。编辑/恢复默认的操作行用 Compose 1.12 稳定 FlowRow 重载，正常宽度保持横排，大字体/窄屏可换行。
- 顶栏仍为24dp图标/48dp触区，底栏24dp图标/11sp标签/至少64dp高度、五等宽与安全区不变；标题/标签单行省略但保留完整语义文本，不限制 fontScale。
- 本篇不实现应用级减少动态设置；其后的[第7项运动策略](2026-10-20-reduced-motion.md)复用平台MotionDurationScale并增加应用偏好，自有门控提升至AppMotion，不改变本篇透明效果和语义契约。

## Evidence

- [Apple 减少透明效果](https://developer.apple.com/documentation/uikit/uiaccessibility/isreducetransparencyenabled)与[通知](https://developer.apple.com/documentation/uikit/uiaccessibility/reducetransparencystatusdidchangenotification)：EXA及Apple DocC Objective-C声明确认 `UIAccessibilityIsReduceTransparencyEnabled()`、`UIAccessibilityReduceTransparencyStatusDidChangeNotification`，iOS8起可用；默认通知中心、主队列回调。
- [Compose 1.12 FlowRow 源码](https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.0/compose/foundation/foundation-layout/src/commonMain/kotlin/androidx/compose/foundation/layout/FlowLayout.kt)：无 overflow 参数的稳定重载，不用废弃的实验 overflow 重载。
- [Compose 焦点](https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior)与[错误/liveRegion语义](https://developer.android.com/develop/ui/compose/accessibility/semantics)：复用标准选择项的焦点，不另加 focusable。

## Existing decisions audit

- [Melox玻璃](2026-10-20-melox-glass.md)：部分重叠，保留采样/几何/预设，实现其系统透明效果降级缺口并互链。
- [材质选择](2026-10-20-material-selection.md)：部分重叠，四项默认与持久化不变，系统限制只影响有效显示。
- [底栏布局](2026-10-20-stacked-bottom-navigation.md)：部分重叠，保留五等宽项、字号与安全区，在其语义边界内修复。
- [传感器](2026-10-20-glass-gravity-sensor.md)：部分重叠，门控继续由渲染层提供，不新增采集器。
- 未发现同范围活跃提案；不变更设置存储和导航架构。

## Alternatives considered

- 仅保留应用开关最省代码，但系统要求降低透明效果时仍绘制玻璃，故补平台输入。
- 把系统状态写回应用偏好能复用持久化，但会丢失用户选择、阻止系统恢复后的正确显示，因此只在根层合并。
- 重新打开默认 indication 能显示焦点，但引入另一套按压反馈，且真实行上的轮廓会被后绘制的 Lens 覆盖；保留现有按压效果，最后只画无交互焦点轮廓。
- 为验收新建网页仿真或模拟传感器不会验证 Compose 字形、读屏树和 Skia shader；不以替代渲染伪装双端验收。

## Accessibility design review

以下均是本项源码判断，非屏幕/设备验收：Purpose符合（不加业务占位）；Agency符合（偏好保留可恢复）；Responsibility符合（系统值不落盘、观察者释放）；Familiarity符合（保留Miuix选择/按钮和标准Tab）；Flexibility部分符合（焦点/错误/换行已修，读屏和200%字体待设备）；Simplicity符合（复用单门控和现有状态）；Craft待设备（复杂背景对比、轮廓像素与帧耗）；Delight部分符合（保留玻璃交互，统一减少动态仍属第7项）。

## Testing

- 新增1项 Kotlin 门控测试遍历应用/系统透明限制与shader支持的8种组合；累计133项声明，CI门槛同步133，Kotlin尚未执行。
- 新增5项Python源码守卫：系统输入合并且不落盘、UIKit通知/前台刷新/清理、实体回退停采样、焦点轮廓前景与唯一语义、字段错误/liveRegion及FlowRow。旧材质/玻璃/底栏守卫同步三输入门控。
- 结构/链接/笔记格式检查通过，完整Python64项通过，git diff --check通过。源码守卫不执行Compose语义树、布局或UIKit通知。

## Consequences

恢复系统透明效果后会按原选择重新采样，无新依赖、存储格式或导航状态。新增平台文件均少于40行；焦点轮廓使用局部装饰状态。

当前授权仅本地源码与轻量检查，未运行Gradle/Actions/Xcode、未提交推送。UIKit Kotlin/Native互操作、VoiceOver/TalkBack、焦点进出/键盘激活、200%字体、RTL/旋转、复杂背景下对比与字形折射、无硬件/采集错误及帧耗均保留设备验收，见 [UI验收](../../../../docs/ui-showcase.md)。6.4源码与静态部分完成不等于完整无障碍验收通过。
