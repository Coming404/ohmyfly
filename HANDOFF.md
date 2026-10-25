# 交接文档：OhMyFly（原 ohmydrift）

> 生成时间: 2026-10-25 18:05 Asia/Shanghai | 分支: `fix/mirror-page-polish`（HEAD `284c87f`，与 origin 同步） | 未提交改动: U10+U11 代码与文档约 34 项（含新增未跟踪 `SettingsSessionInitialReadTest.kt`）
>
> 本文是会话快照；状态权威来源仍是 [docs/context/NOW.md](docs/context/NOW.md)。旧快照已归档到 `handoff/history/handoff-20261025-180400-u10fix.md`。

## 1. 会话目标 (Goals)
- 按 `docs/device-feedback-rework.md` 第 8 节分批实施真机反馈需求 U1–U12。每开启下一批前必须先向用户确认，并说明任务名称与委派对象。
- 当前批次：U10（启动无加载界面、首读成功前不写盘、读失败弹窗可重试）+ U11（删除旧格式兼容，保留设置页已有子页与配置）。要先收口这批，再推送跑 Actions。
- 验收：审查者复核无阻断/应修 → 用户授权后提交（必须包含新增测试文件）、推送 → Release 双端构建与 Kotlin 测试通过（commonTest ≥179）。

## 2. 当前状态
### 已完成
- 第一批 U5–U9 改名：提交 `3122e90` 并推送，Release run `39986688054` 双端 success。仓库已改名为 `Coming404/ohmyfly`，本地 origin 已同步。
- CI 优化：缓存 `~/.konan`、删除 AAB，提交 `284c87f` 并推送，Project checks `39989134703` success。
- U10+U11 本地代码：执行者第三次交付，并经审查 → 返工 → 再审查。上轮两个阻断问题（测试括号错误、弹窗在 Scaffold 外不显示）已修；`writeUncertain` 区分两种提示；“拼接方式”恢复为只读行；建议 2–5 已落实。
- 主会话已补改 PRD 第 9、25、46 行：拼接方式改为只读行，P-M05 下拉页标为已取消。
- 最近验证（2026-10-25）：`python3 tools/check_project.py` PASS；`python3 -m unittest discover -s tools/tests` 69 tests OK；`git diff --check` 无输出；commonTest `@Test` 179 = HEAD；改动的 Kotlin 文件括号全部配平。**未运行 Gradle/Kotlin 编译**（规则禁止本地编译）。

### 进行中（用户已决定“现在修”，尚未派发）
U10+U11 第二次返工，内容见第 6 节第 1 步。委派对象：执行者；修完派审查者复核。

## 3. 关键决策 (Decisions)
已写入 `docs/context/DECISIONS.md` 新决策表：
- **U11 保留起飞、流媒体与 AI、塔台与过滤子页及其配置，只删旧兼容** — 理由：这些是现有功能，不是旧格式。
- **“拼接方式”为只读行（显示 HTTP(S) 前缀，无下拉、无箭头），U3/AC-M12 保留** — 理由：这是 U3 已确认的可见控件；改成下拉或删掉都已被否决。
- **失败弹窗沿用 Overlay（MirrorDialog），放在 App.kt 的 Scaffold content 内** — 理由：Overlay 依赖 Scaffold 宿主；不改用 WindowDialog。
- **2026-10-25：“名称”“拼接方式”“地址”三个标题统一为 13sp Normal** — 用户指定 13sp，覆盖 PRD/AC-M12 原写的 16sp；拼接方式也要从 16sp 改为 13sp。“改写预览”标题不在 AC-M12 三项内，按审查建议保持现状（如需改，先问用户）。
- **2026-10-25：失败弹窗拦截返回手势，不播放缩小动画** — 理由见第 5 节 N1。

## 4. 改动文件 (Files)
未提交改动（`git status --short` 可见全部），主要有：
- `shared/src/commonMain/kotlin/com/ohmyfly/PersistedSettings.kt`：装配仓库与 Session；仓库构造失败时递增 retry 重建；第 6 个回调参数是重试动作。
- `shared/src/commonMain/kotlin/com/ohmyfly/App.kt:235–243`：失败弹窗（`dismissible = false`，两种文案，“重试”按钮）。
- `shared/src/commonMain/kotlin/com/ohmyfly/settings/SettingsSession.kt`、`SettingsUiState.kt`：`confirmed == null` 时拦截所有写入；`settings` 改为非空；新增 `writeUncertain`。
- `shared/src/commonMain/kotlin/com/ohmyfly/ui/settings/MirrorDialog.kt`：新增 `dismissible` 参数（默认 true）。
- `shared/src/commonMain/kotlin/com/ohmyfly/ui/settings/MirrorEditor.kt:123–151`：`MirrorPrefixRow()`（当前 16sp）、`EditorLabel`（当前 12sp）。
- 删除 `mirror/MirrorMode.kt`；修改 `MirrorEntry.kt`、`MirrorUrl.kt`、`MirrorCodec.kt`、`SettingsCodec.kt`（`clear()` 后重建）、`SettingsValidation.kt`、`MirrorDraftState.kt`、`MirrorProbeState.kt`、`MirrorPageState.kt`、`MirrorIcons.kt`（删 ChevronDown）。
- 测试：新增 `shared/src/commonTest/kotlin/com/ohmyfly/settings/SettingsSessionInitialReadTest.kt`（**未跟踪，提交时必须纳入，否则测试数 177 <179，CI 失败**），另改了 10 个 commonTest 文件；`tools/tests/test_mirror_redesign.py`、`test_optimistic_settings.py`。
- 文档：`docs/settings.md`、`docs/requirements.md`、`docs/device-feedback-rework.md`（U10/U11 状态列）、`docs/design/mirror-settings/PRD.md`、`docs/context/{NOW,DECISIONS}.md`、本文件。

## 5. 未决问题 & 阻塞
- **N1（应修）**：失败弹窗不可关闭。Android 完成一次预测返回手势后，弹窗停在缩小状态，遮罩变淡甚至消失。依据：Miuix v0.9.4（commit `39c40f99844227b853f0049a0933b1f3ae6c00ba`）`miuix-ui/.../layout/DialogContentLayout.kt:191–215`，`onBackCompleted` 只调用 `requestDismiss()`，InProgress 修改 `backProgress`/`dimAlpha`，只有取消时才 `resetGesture()`（主会话已亲自核对源码）。普通返回键和取消手势正常。
- **N2（应修，用户已定 13sp）**：AC-M12 三个标题字号不一致。
- **S1（文档漂移）**：PRD 第 70 行 I-M09 仍写下拉/草稿；第 87 行“其余镜像字段保持兼容”需限定为当前前缀格式；`docs/settings.md:57` 后半句“不使用灰色遮罩”与默认灰幕不符；PRD 第 9、46、99 行的 16sp 要改为 13sp。线框原型（`docs/design/mirror-settings/index.html`、`build_wireframe.py`）仍有 P-M05 下拉页，属过时设计稿，未决定是否更新。
- **S2**：测试中 4 处多余 `!!`：`MotionSettingsSessionTest.kt:29,33,37`、`MirrorManualSaveTest.kt:65`。
- 模型异常：执行者固定用「666 · GPT 6.1 Sol」，曾连续 3 次中途无回复；最近一次返工回调显示实际模型为 DeepSeek V4.1 Flash。模型由用户手动指定，主会话不切换。

## 6. 下一步 (Next Steps)
- [ ] **派执行者做 U10+U11 第二次返工**（用户已同意，无需再问）。留档目录 `/var/minis/shared/team-workbench/runs/20261025-executor-persistence-fix2/`（先建 `result.json`）。简报须包含：目标/需求来源、授权与禁止、可改范围、禁改范围、已定决策、验证方式、停止条件、报告路径与格式。内容：
  1. N1：在 `dismissible = false` 时，用公开的返回处理 API（如 Compose/NavigationEvent 的 `NavigationBackHandler`，优先级要高于 Miuix 内部的那个）消费返回手势，不让 Miuix 收到预测进度。不换 Window、不调用 internal API。要覆盖“完成手势/取消手势/返回键”三种情况：都不关闭、不残留缩小、遮罩不丢；重试成功后正常退场。先查 v0.9.4 源码核实 API 与优先级。
  2. N2：`MirrorEditor.kt` 中“名称”“地址”标题与“拼接方式”统一为 13sp Normal，同色同图标间距，不改“改写预览”；同步 PRD 第 9、46、99 行和 `docs/settings.md:57`，Python 守卫检查三项都是 13sp。
  3. S1 文档收口（线框原型不动）；S2 删 4 处 `!!`。
  4. 验证：`check_project.py`、unittest、`git diff --check`、一次性括号配平脚本（用完删）、`@Test` ≥179。禁止编译、提交、推送。
- [ ] **主会话核对 → 派审查者复核**（留档 `runs/20261025-reviewer-persistence-fix2/`）。
- [ ] **⚠需用户授权**：提交（逐个暂存文件，包含新增测试文件）、推送 `fix/mirror-page-polish`、触发 Release 构建：`GH_TOKEN="$GITHUB_TOKEN" gh workflow run build.yml -R Coming404/ohmyfly --ref fix/mirror-page-polish -f platform=both`。
- [ ] **⚠需用户确认后开启**：U12（iOS 输入框单击不弹工具栏，双击/长按显示系统工具栏；入口 `shared/src/iosMain/kotlin/com/ohmyfly/ui/settings/SettingsInputMenu.ios.kt`，守卫 `tools/tests/test_mirror_redesign.py:79–81`；上次因断网失败，零改动）→ 第三批 U1+U2 弹簧动画、U3 其余 + U4。

## 7. 恢复指引 (How to Resume)
1. 项目根目录 `/var/minis/workspace/ohmydrift`。先读 `AGENTS.md`、`CLAUDE.md`、`PROJECT_INDEX.md`、`docs/context/NOW.md`，再读本文件。
2. 读取 `/var/minis/skills/team-workbench/SKILL.md`（协作工坊派发规则，只读一次）。
3. 安全核验：`cd /var/minis/workspace/ohmydrift && git status --short --branch && python3 tools/check_project.py && python3 -m unittest discover -s tools/tests`。预期分支 `fix/mirror-page-polish`、约 34 项改动、69 tests OK。
4. 报告参考：上轮审查 `/var/minis/shared/team-workbench/runs/20261025-reviewer-persistence-fix/output.md`；返工报告 `runs/20261025-executor-persistence-fix/output.md`。
5. 注意：同一文件不要并行编辑（曾互相覆盖）；`docs/context/history/`、`.agents/notes/` 是只读存档。

## 8. 环境 / 约束
- Minis（iOS）+ iSH Alpine aarch64。Kotlin 2.4.20、Compose Multiplatform、Miuix 0.9.4。本地不编译，构建走 GitHub Actions。
- 推送用内联凭据 helper，读取环境变量 `GITHUB_TOKEN`：`git -c credential.helper= -c credential.helper='!f(){ echo username=x-access-token; echo "password=$GITHUB_TOKEN"; }; f' push`。gh 命令用 `GH_TOKEN="$GITHUB_TOKEN"`。不输出变量值。
- 提交、推送、触发构建、改仓库名、开启下一批任务都需用户明确授权。子代理模型由用户手动指定，主会话不选、不改。
