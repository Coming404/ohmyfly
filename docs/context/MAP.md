# MAP

目录、版本、配置与构建位置的对应关系。具体版本号以配置文件为准，本页不复制。

## 目录

| 路径 | 内容 |
| --- | --- |
| `androidApp/` | Android 宿主 |
| `iosApp/` | Swift 宿主与 Xcode 工程 |
| `shared/src/commonMain/` | 共享 Miuix 页面、设置、玻璃材质等 |
| `shared/src/androidMain/`、`shared/src/iosMain/` | 平台实现 |
| `shared/src/commonTest/` | Kotlin 共享测试 |
| `native-core/` | Go 核心预留，未接入，见 [native-core/README.md](../../native-core/README.md) |
| `tools/` | 本地轻量检查与 CI 打包校验脚本；`tools/tests/` 为其 Python 测试 |
| `.agents/notes/` | 停用 skill 留下的历史决策存档，只读；新决策写 [DECISIONS.md](DECISIONS.md) |
| `docs/` | 需求、规范、设计与证据；`docs/design/` 线框，`docs/evidence/` 去重审查证据 |
| `third-party/` | 模板来源与许可证 |
| `handoff/history/` | 旧交接快照（历史，不默认读取） |
| `.signing/`、`.ops/` | 已被 `.gitignore` 忽略的本地私有内容；不读取签名内容 |

## 版本与配置

| 对象 | 位置 |
| --- | --- |
| 依赖、SDK、最低系统版本 | `gradle/libs.versions.toml` |
| Gradle Wrapper | `gradle/wrapper/gradle-wrapper.properties` |
| 模块声明 | `settings.gradle.kts`（禁止 Desktop/Web 目标） |
| 版本选型依据 | [research.md](../research.md) |

## 构建与部署

| 对象 | 位置 |
| --- | --- |
| 远程仓库 | GitHub `Coming404/ohmyfly`（私有） |
| 轻量检查 | `.github/workflows/checks.yml`（push、PR、手动触发） |
| 双端 Release | `.github/workflows/build.yml`（仅手动，`platform`: android / ios / both） |
| 产物与签名边界 | [release-builds.md](../release-builds.md) |
| 部署 / 上架 | 无；分发方案与许可待定 |
