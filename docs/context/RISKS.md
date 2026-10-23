# RISKS

有证据的问题、风险、验证缺口与保护措施。核验：2026-10-23，仅依据下列来源文档，未重新运行代码或设备测试。

## 验证缺口

| 缺口 | 现状 | 来源 |
| --- | --- | --- |
| 真机验收 | 镜像页键盘、读屏、延迟色阶、弹窗视觉未在 Android/iOS 验收；玻璃像素、传感器、大字体、帧耗未验收 | [mirror-page-polish](../../.agents/notes/implemented/feature/2026-10-22-mirror-page-polish.md)、[release-builds.md](../release-builds.md#构建与验证边界) |
| 最低系统 | iOS 16 / Android 13 真机运行未证明 | [development.md](../development.md#尚未证明) |
| 真机 12 项反馈 | 设置卡片点击迟滞、玻璃灰膜、冷启卡顿无真机时间线或帧采样，不能宣称已修 | [device-feedback-rework.md](../device-feedback-rework.md) |
| 运动策略 | 应用级减少动态未覆盖全部 Miuix 内置动画 | [settings.md](../settings.md#运动偏好与切换过渡) |
| 本地检查范围 | `tools/check_project.py` 与 Python 测试不编译 Kotlin，不能代表构建或视觉验收 | [README.md](../../README.md#本地轻量验证) |

## 产品与发布风险

- Go 核心未接入，塔台导入与起飞未实现；不得填充假结果。来源：[requirements.md](../requirements.md)。
- `com.ohmyfly.app` 仅为开发标识；正式包名、签名团队、图标未确定。来源：[development.md](../development.md#发布前必须处理)。
- 计划接入的 Go 上游有 GPL 义务，最终许可与分发方案待定。来源：[README.md](../../README.md#构建与分发)。
- 版本目录只固定直接依赖，无完整传递依赖锁。来源：[development.md](../development.md#发布前必须处理)。
- iOS IPA 未签名，需用户自行重签。来源：[release-builds.md](../release-builds.md)。

## 保护措施

- 不记录塔台令牌、代理凭据、签名密钥；`.signing/`、`.ops/`、`.env` 已被 `.gitignore` 忽略。
- 不在本地下载 SDK/NDK/Gradle，完整构建只在 GitHub Actions 运行。
- Git 提交、推送、删除 Actions 记录、触发构建需用户当次授权。
