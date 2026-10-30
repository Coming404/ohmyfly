# Agent Note: 第 8 项 GitHub 镜像页按线框还原

Status: implemented

## Problem

原镜像页是整页编辑、行内管理和顶部测试操作，与九张独立图片及流程总览不一致；还把乐观列表相等误当保存完成。用户在实施中确认：普通拼接行不套卡片、底部手动保存、镜像支持 HTTP(S)／裸域名、无直连条目、空列表纯图标文字提示。

## Decision

- 稳定ID管理使用 Miuix 0.9.4 OverlayIconDropdownMenu 的测试／编辑／删除，移除管理弹窗与上／下移动。全量测试后自动选最低成功延迟；镜像结果保留由AppContent owner管理，不影响镜像源持久化回退顺序。
- OverlayDialog 使用 Miuix 默认半透明 windowDimming 遮罩及18dp阴影，无背景模糊；拼接行用官方OverlayDropdownPopup提供链接图标、16sp Normal标题与ChevronDown。
- MirrorPageState 只拥有编辑/删除弹窗和会话草稿，MirrorDraftState 保留 TextFieldValue 游标／合成态，不是第二设置Store。拼接方式使用带自定义锚行的官方OverlayDropdownPopup，已有Template仅显示禁用项，可显式切换前缀。
- HTTP／HTTPS 显式保留，裸域名补 HTTPS，www不增删，路径大小写保留；normalize／dedupe／preview 使用同一规则。旧 Template 不静默转换。传输和同源跳转支持 HTTP，HTTPS→HTTP 拒绝；原始 GitHub 与其他设置 URL 仍要求 HTTPS，公网DNS／凭据／重定向／大小／摘要限制不削弱。
- 应用范围的runner/results由AppContent持有、仅镜像条目增删／endpoint变动清除关联结果；列表始终成功结果升序、失败／未测尾置，完整全量测试完成后一次性选最快成功镜像。该显示/自动选择不改变镜像源存储次序及资源层回退路线。
- Android cleartext、iOS ATS 放开用户动态 HTTP 域名；镜像应用层仍严格校验，HTTP 本身不提供加密。iOS设置输入框局部覆盖已核实公共 TextToolbar，隐藏系统浮动编辑菜单，不改 Android／只读预览；未用私有API或全局 UIKit swizzling。

## Existing decisions audit

- 应用保留原独立设置页导航与显式 reduced-motion 策略。顶部五个主目的地统一使用 Miuix springAnimateToPage，设置栈内部继续用NavDisplay MiuixDefault转场；标准滚动容器由MiuixTheme的MiuixOverscrollFactory提供单一回弹效果。
- [真机反馈提案](../../proposed/feature/2026-10-20-device-feedback-rework.md)：只接管第8项镜像局部，其余范围仍 proposed，不能把本项记作全部反馈完成。
- [官方选择菜单](2026-10-19-preset-selection-popup.md)：其他设置菜单不变；镜像单选项需要特殊材质／父弹窗外点边界，采用普通设置行＋单层覆盖区。

## Alternatives considered

- 仅调现有整页编辑的样式：改动少，但无法得到图中的模态替换与底部固定操作。
- 自动保存／关闭时提交：减少按钮，但用户已明确改为手动保存，不保留并行隐式路径。
- 编辑弹窗嵌套 OverlayDropdownPreference：自带状态语义，但单选项会增加模态堆叠和遮罩边界；复用基础行、同层选项更直接。
- 维持仅HTTPS：默认更安全，但不满足用户明确HTTP输入需求；开放平台运输限制，同时保持应用层只读公开镜像边界，不通过放松原始GitHub或其他URL规则实现。

## Consequences

源码组件按UI／页面草稿／Session／领域／运输边界拆分，单文件均低于300行；Session逻辑超过200行，职责仍是同一顺序事务与确认生命周期，继续保持内聚而不增空壳接口。无新增依赖。图标仅补库中没有的本地Lucide路径，许可保留于设计licenses并在文件头标来源。

Kotlin静态回归目前声明178项，CI门槛同步；75项Python轻量检查和项目结构/diff检查通过。双端Kotlin构建未运行，真机渲染仍待验。初版路线说明及测试统计是实施历史，不代表本轮镜像页精修已通过Kotlin编译或真机验证。
