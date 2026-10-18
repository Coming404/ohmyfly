# ohmydrift — development instructions

## Scope and source of truth

- Build a Kotlin + Compose Multiplatform app for Android and iOS with shared UI/business logic and Miuix. Use Miuix Scaffold for page structure; do not substitute Material Scaffold or add Desktop/Web targets without a requirement.
- iOS minimum: 16.0. Android minimum: provisionally API 31 / Android 12. Do not raise either silently. The independent miuix-blur module requires API 33 and is not a baseline dependency.
- Read `docs/requirements.md`, `docs/engineering.md`, and existing decisions before changing cross-module contracts.
- Find upstream documentation and repository entry points in `docs/references.md`. For version-sensitive APIs, inspect the pinned release/tag, not only the latest website or main branch. Add/update the relevant reference when introducing a dependency or integration.
- `gradle/libs.versions.toml` and the Gradle Wrapper define declared build versions. A declared pin is not proof that the entire transitive graph is locked or that the build passes.
- Local work is editing and lightweight checks. Full Kotlin/Android/iOS builds run in GitHub Actions; do not download SDK/NDK/Gradle distributions locally unless explicitly requested.
- Go handles proxy connections, transfer, timing and statistics. Kotlin receives bounded events/results, never the speed-test payload. Do not add a system VPN, external subscription conversion service or independently launched mihomo executable by default.
- Do not log subscription tokens, proxy credentials, signing keys or credential-bearing dedup fingerprints. Preserve upstream provenance and license obligations.

## Kotlin Multiplatform library selection

When adding or recommending Kotlin Multiplatform dependencies, query the **klibs.io MCP** (`https://api.klibs.io/mcp`) before choosing a library. Use the configured `klibs` server; do not add a duplicate server.

1. Search relevant projects and confirm the exact owner, artifact and targets. At minimum check Android plus `ios_arm64` and `ios_simulator_arm64`; “KMP” alone is not sufficient.
2. Verify the Maven `groupId:artifactId`, available versions, latest stable version **when reported**, Kotlin/toolchain metadata and comparable alternatives. Prefer the smallest dependency that solves the actual need; latest does not automatically mean compatible.
3. Cross-check minimum OS requirements, API maturity, license and maintenance/activity against the official repository, LICENSE, release notes and package metadata. **The current MCP schema does not return every one of these fields.** Do not invent a license or maintenance guarantee from a version-search response.
4. `latestStableVersion: null` means no stable version was reported by the index; it is not equivalent to GitHub prerelease status and must not be described as a stable API guarantee. Empty search results do not prove an artifact or target is unsupported.
5. If MCP is unavailable, disclose that and use official sources for a provisional assessment. For a new dependency, record the missing check and defer treating it as verified; never claim an MCP query succeeded when it did not.
6. Record the query/result summary, source links, chosen version, platform constraints and important rejected alternative in the related decision or `docs/research.md`. Do not use floating versions (`+`, `latest.release`, unpinned branches).

## 重要改动必须留笔记

The rules below are project-local and require no external development-rule package.

1. 非平凡改动（行为、架构、跨文件契约、流程/工具链、测试策略、落盘/网络格式）前，检索 `.agents/notes/`，更新已有归属或先写提案。机械性小改不强制创建笔记，也不意味着自动授权提交。
2. 目录采用 `.agents/notes/{proposed,implemented,rejected,archived}/{architecture,feature,bug-fix,simplification,process,testing}/`，只创建实际使用的目录。
3. 新方案使用 `Status: proposed`；真正落地后随代码改动转为 `implemented`。部分落地不能把未来能力标为完成。架构改变需新记决策并互链，已拒绝提案不要伪装为已实现归档。
4. 被放弃的方案先写它最强的理由，再解释为什么不用。笔记至少说明问题、方案/决定、替代项和验收/风险；改动相关文档同步更新。
5. 交付前运行 `python3 tools/check_project.py` 和 `python3 -m unittest discover -s tools/tests -v`。本地静态通过不等于 Kotlin 编译、签名或真机验证通过；明确报告未运行项。不要求 `npm run verify-notes`。

## Working boundaries

- Do not modify files outside this project as part of routine implementation.
- Only clean explicitly identified disposable research/build artifacts. Preserve evidence needed to reproduce a decision and list removed paths.
- No push, release, paid workflow, signing or public publication is implied by project setup. Secrets belong in the chosen secure CI mechanism, not source files.
