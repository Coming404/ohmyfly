# PROJECT_INDEX

OhMyFly：Android / iOS 塔台管理与航班起飞应用，Kotlin + Compose Multiplatform + Miuix，Go 核心尚未接入。

本页是条件路由：只打开与当前任务相关的条目，并在目标文件内继续定位章节。读写规则见 [AGENTS.md](AGENTS.md#项目文档读写规则)。

## 任务路由

| 任务 | 先看 |
| --- | --- |
| 接续工作、了解当前进展 | [docs/context/NOW.md](docs/context/NOW.md) |
| 项目规则、验证要求、依赖选型流程 | [AGENTS.md](AGENTS.md) |
| 项目用途与基本使用 | [README.md](README.md) |
| 产品需求与范围 | [docs/requirements.md](docs/requirements.md) |
| 工程形态、视觉规则、Go 桥接 | [docs/engineering.md](docs/engineering.md) |
| 五页功能分工 | [docs/page-functions.md](docs/page-functions.md) |
| UI 实现与验收 | [docs/ui-showcase.md](docs/ui-showcase.md) |
| 设置页与 GitHub 镜像 | [docs/settings.md](docs/settings.md)；镜像页设计 [docs/design/mirror-settings/PRD.md](docs/design/mirror-settings/PRD.md) |
| 真机 12 项反馈修正方案 | [docs/device-feedback-rework.md](docs/device-feedback-rework.md) |
| 定位目录、版本、配置、构建位置 | [docs/context/MAP.md](docs/context/MAP.md) |
| 执行检查、构建、发布操作 | [docs/context/RUNBOOK.md](docs/context/RUNBOOK.md) → [docs/release-builds.md](docs/release-builds.md) |
| 涉及既有选择或新决策 | [docs/context/DECISIONS.md](docs/context/DECISIONS.md) |
| 已知风险、验证缺口 | [docs/context/RISKS.md](docs/context/RISKS.md) |
| 查官方资料、上游源码 | [docs/references.md](docs/references.md) |
| 版本选型依据 | [docs/research.md](docs/research.md) |
| 开发顺序 | [docs/development.md](docs/development.md) |
| 追溯历史过程 | [docs/context/history/README.md](docs/context/history/README.md)（先搜索再读） |
| 第三方来源与许可 | [third-party/README.md](third-party/README.md) |

## 权威来源

| 事实类别 | 唯一权威来源 |
| --- | --- |
| 长期规则 | [AGENTS.md](AGENTS.md)（[CLAUDE.md](CLAUDE.md) 只补充风格，指回 AGENTS） |
| 当前目标、进展、阻塞、下一步 | [docs/context/NOW.md](docs/context/NOW.md) |
| 声明的依赖与工具版本 | `gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties` |
| 已确认决策 | [DECISIONS.md](docs/context/DECISIONS.md)（`.agents/notes/` 为停用的只读历史存档） |
| 构建产物、签名与安装边界 | [docs/release-builds.md](docs/release-builds.md) |
| 风险与验证缺口 | [docs/context/RISKS.md](docs/context/RISKS.md) |
| 实际构建结果 | GitHub Actions run 本身；文档只记录 run 编号与核验日期 |

`HANDOFF.md` 是会话交接快照，不是状态权威来源。
