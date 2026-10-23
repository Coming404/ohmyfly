# NOW

当前状态唯一来源。状态实质变化时直接修订本页，旧过程归档到 [history](history/README.md)。

核验：2026-10-23，对象为本地 Git 状态与 GitHub Actions 最近 6 次 run（只读查询）。

## 当前目标

GitHub 镜像源页精修（第 8 项的镜像部分）在分支 `fix/mirror-page-polish` 上完成并等待用户验收；用户已提出继续改进需求 U1–U11，待确认细节后实施。

## 进展

| 项目 | 状态 |
| --- | --- |
| 分支 | `fix/mirror-page-polish`，HEAD `ca65ec9`，与远端同名分支一致 |
| `origin/main` | `453889c`（镜像页重建） |
| 代码 | 镜像页精修已实现，规格见 [镜像页 PRD](../design/mirror-settings/PRD.md) |
| 构建 | `ca65ec9`：Project checks `39805162120` success；Release device builds `39805362270` success（android、ios 作业均 success） |
| Kotlin 测试数 | 待核实（CI 门槛 178 项，未核对该 run 的 JUnit 实际数） |
| 用户验收 | 未完成：真机视觉、键盘、读屏、弹窗视觉未验收 |
| 合并 / PR | 未合并入 main；PR 未创建 |
| 本地未提交 | `AGENTS.md` 已修改（用户改写为中文通用准则 + 文档规则）、`CLAUDE.md` 未跟踪；context 文档与 2026-10-23 需求更新也未提交 |

## 真机反馈阶段

需求与方案见 [device-feedback-rework.md](../device-feedback-rework.md)；本表只记状态。

| 阶段 | 状态 |
| --- | --- |
| A 平台与触控 | 提案，未授权 |
| B 紧凑布局与字体 | 提案，未授权 |
| C 玻璃与性能 | 提案，未授权 |
| D 设置即时编辑 | 镜像页已实现并构建通过，真机未验收；原“自动保存”方案已被手动保存替代。其余设置页未实施 |
| E 弹层与运动 | 提案；U1、U2 已并入 |
| F 震动与回归 | 提案，未授权 |
| 继续改进 U1–U12 | 细节已全部确认，未实施；见[第 8 节](../device-feedback-rework.md#8-继续改进需求2026-10-23) |

## 阻塞与待定

- 是否合并 `fix/mirror-page-polish` 到 main：待用户决定。
- U1–U12 实施：已授权分批进行，每批开始前需用户确认。第一批 U5–U9 改名已本地完成并经审查者复核（未发现源码/配置问题），现行文档术语已批量替换，GitHub 仓库已改名为 `Coming404/ohmyfly`；未提交、未构建。其余未开始。
- 业务主线（塔台导入、航班目录、Go 起飞）未接入。

## 下一步

1. 用户真机验收镜像页（Android APK、iOS 自行重签 IPA）。
2. 按用户指令决定合并、开 PR，或开始实施 U1–U12。

以上均需用户明确授权，本页不构成执行授权。

## 证据入口

- Actions：<https://github.com/Coming404/ohmyfly/actions>
- 风险与缺口：[RISKS.md](RISKS.md)
- 操作方法：[RUNBOOK.md](RUNBOOK.md)
