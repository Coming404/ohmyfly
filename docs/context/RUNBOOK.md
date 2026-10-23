# RUNBOOK

有依据的操作步骤。文档中的步骤不构成执行授权；带外部副作用的操作需用户当次授权。

## 本地轻量检查

前置：`python3`、`rg`。不依赖 Gradle 或移动端 SDK。

```sh
python3 tools/check_project.py
python3 -m unittest discover -s tools/tests -v
git diff --check
```

验证：命令退出码为 0。只覆盖配置、文件关系、Markdown 本地链接与 Agent Note 格式，不编译 Kotlin。

最近执行：2026-10-23 文档整理时运行了 `check_project.py` 与 `git diff --check`；Python 单测本次未运行。

## 云端构建（需授权）

不在本地运行 `./gradlew`，也不下载 SDK/NDK/Gradle。

| 工作流 | 触发 | 内容 |
| --- | --- | --- |
| Project checks（`checks.yml`） | push、PR、手动 | 上述轻量检查 |
| Release device builds（`build.yml`） | 仅手动，`platform` = android / ios / both | Android host tests + Release APK/AAB；iOS arm64 Release IPA（未签名） |

手动触发示例（本文档整理时未执行）：

```sh
GH_TOKEN="$GITHUB_TOKEN" gh workflow run build.yml -R Coming404/ohmyfly --ref <branch> -f platform=both
GH_TOKEN="$GITHUB_TOKEN" gh run list -R Coming404/ohmyfly -L 5
```

验证：以 run 的 conclusion 和各 job 结果为准，工作流配置存在不代表构建通过。产物、签名与安装说明见 [release-builds.md](../release-builds.md)。

## 恢复上下文

```sh
git status --short --branch
git log -4 --oneline
```

然后读 [NOW.md](NOW.md)，按 [PROJECT_INDEX.md](../../PROJECT_INDEX.md) 只打开当前任务相关文档。

## 未覆盖

- 真机安装与验收流程：待核实，项目内无可复用的书面步骤。
- 发布与上架：无，分发方案未定。
