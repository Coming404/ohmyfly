# ohmydrift

Android / iOS 订阅管理与节点测速应用的最小工程基础。

**Kotlin + Compose Multiplatform + Miuix**；Go核心尚未接入。当前共享 UI 框架包含五个入口：**概览、订阅、测速、节点库、设置**；采用固定品牌顶栏和五项悬浮胶囊导航。节点选择与外观使用演示/会话数据，订阅导入和真实测速尚未接入，参见 [UI 实现说明](docs/ui-showcase.md)和[五页功能分工](docs/page-functions.md)。**上一版 `6cb51eb` 已通过 Android/iOS Release 云端构建；本轮五页改动尚未编译，不代表已完成业务或可直接发布。**

## 开始开发

1. 先读 [AGENTS.md](AGENTS.md)：开发边界、选库前查询 Klibs MCP、决策记录与验证要求。
2. 需求：[docs/requirements.md](docs/requirements.md)。
3. 工程与视觉：[docs/engineering.md](docs/engineering.md)。
4. **资料入口：[docs/references.md](docs/references.md)**。包括官方文档、版本/源码、Go核心与Actions入口；查API时优先锁定标签。
5. 版本与限制：[docs/research.md](docs/research.md)。
6. 下一步：[docs/development.md](docs/development.md)。

## 目录

```text
androidApp/       Android宿主
shared/          共享Miuix页面；Android/iOS平台实现
  src/commonMain/
  src/androidMain/
  src/iosMain/
  src/commonTest/
iosApp/          Swift宿主和Xcode工程
native-core/     Go核心预留，尚未接入
.agents/notes/   已实现/待实现的技术决定
docs/            需求、规范、资料与证据
tools/           不下载构建工具的本地检查
third-party/     模板来源与许可证
```

## 当前声明

- 产品名：ohmydrift。
- iOS deployment target：16.0；依赖和真机兼容待验证。
- Android minSdk：33 / Android13，用户已确定。
- Kotlin 2.4.20、Compose Multiplatform1.12.0、Miuix0.9.4、AGP9.4.1、Wrapper9.7.1。
- Miuix 0.9.4 的全部正式模块均显式引入：ui、core、preference、icons、blur、shader、squircle、nav；图标使用完整 miuix-icons，不限于 Basic。Android 最低 API33，iOS 仍为16.0。
- `dev.ohmydrift.app`仅为开发标识，正式包名/签名团队尚未确定。
- 参考官方Kotlin/KMP-App-Template的Wrapper和iOS宿主，剔除其他平台及演示业务。来源见 [third-party/README.md](third-party/README.md)。

## 本地轻量验证

```sh
python3 tools/check_project.py
python3 -m unittest discover -s tools/tests -v
```

这些只验证配置、文件关系和文档，不是Kotlin编译器。不要在本地运行Wrapper下载Gradle，完整编译交给GitHub Actions。

## 构建与分发

私有仓库：[Coming404/ohmydrift](https://github.com/Coming404/ohmydrift)。工作流配置不等于构建已通过，实际结果以对应 Actions run 为准。安装与签名细节见 [真机 Release 构建](docs/release-builds.md)。

- 轻量检查：`.github/workflows/checks.yml`。
- Release 构建入口：`.github/workflows/build.yml`，手动选择 Android/iOS/both。
- Android 生成专用测试签名的 Release APK/AAB；iOS 只交付 arm64 真机 Release IPA 原文件，不再分发归档 ZIP，IPA 需要用户自行重签，不是可直接安装的已签名包。
- 应用最终许可与分发方案待定；计划接入的Go上游有GPL义务，模板的Apache许可不替代这些义务。
