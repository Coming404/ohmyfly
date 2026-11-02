# 交接文档：OhMyFly native-core（Go 核心）

> 生成时间: 2026-11-02 15:12 +08:00（17:31 同步提交状态） | 工作树: `/var/minis/workspace/ohmydrift-core` | 分支: `feat/native-core`（无上游，未 push） | HEAD: `6d927d8` | 未提交改动: 以 `git status --short` 为准
>
> 状态来源：任务图 `/var/minis/workspace/multi-agent/runs/20261101-2311-native-core/graph.json`。本文件只是快照。仓库根目录 `HANDOFF.md` 是 App 侧 U10/U11 的旧交接（已跟踪），与本任务无关，未改动。

## 1. 会话目标 (Goals)

- 在 `feat/native-core` 工作树实现 OhMyFly 纯 Go 核心：塔台解析、规范化、指纹去重、gomobile 桥接与 SQLite 存储。
- 验收（graph.json acceptance）：native-core 下 `go vet` / `go test` 通过；双端 gomobile 产物在 Actions 构建成功；主会话审查 diff 后提交。
- 分享链接解析按 SubBoost `SubBoost/subboost@4015347`（完整提交 `401534742802e2b1542783197ce66451bad82739`，参考副本在 `/var/minis/workspace/subboost-ref/`）（AGPL-3.0-only）逐函数移植，文件头注明来源；subs-check-pro 只借鉴。

## 2. 当前状态

### 已完成

- 本地提交（基于 main `3179012`，作者 Coming404 noreply，均未 push）：
  - `b5a1e4d` feat(native-core): towerparse share-link parsers and flight normalize
  - `ce766c3` feat(native-core): hysteria/hysteria2/tuic links and SubBoost lenient URL parity
  - `51e1505` feat(native-core): anytls/wireguard/snell links
  - `a16dd23` towerparse: parse socks, ssh and Telegram proxy links（A2c）
  - `11bb11f` towerparse: recover malformed YAML items, detect quoted root keys, add 10k benchmarks（A2d，5 文件 +852/-144）
- 任务图切片：A 记为 reported；A2a、A2b、A2b2、A2c、A2d 为 accepted；C、D、U1 为 planned。
- 最近一次全量验证（2026-11-02 14:47 +08:00，iSH，go1.27.3，`GOMAXPROCS=1`，`-p 1`）：gofmt、go vet 干净；2 个包 PASS；顶层测试 102 个；`git diff --check` 干净。提交后工作区与 HEAD 只差 README，故验证覆盖已提交代码。生成本文件时未重跑。
- Benchmark（`-benchtime=1x` 单次，各 1 万条，断言 Parsed==10000、Skipped==0）：YAML 5.986 s（219,638,312 B/op，1,613,025 allocs/op）；链接 1.790 s；Base64 链接 2.092 s。噪声未评估；10 万条只按 x10 线性推算，未实测。

### 进行中

- 无。没有运行中的子代理（A2d job `95C2DAF8` 已结束并验收）。用户 2026-11-02 15:04 选择暂停。

## 3. 关键决策 (Decisions)

- **SubBoost 直接移植**：用户 05:51 决定。仓库 LICENSE 尚未定，AGPL 影响待用户拍板。
- **去重用 sublinkPro 内容哈希**：忽略 Name/Dialer_proxy/Udp/Tfo/Client_fingerprint，不移植 SubBoost 去重；尚未实现，时机待定（09:05）。
- **tg 以 13:37 为准**：注册 socks5/socks4/socks/socks5+tls、ssh、tg、t.me 的 socks/http/https 代理链接；普通 http(s) 行跳过，理由是避免把订阅地址误识别为代理。netch 仍不做。graph.json decisions 第一条“tg 不做”已由 14:58 条覆盖，原文保留。
- **DetectFormat 认单双引号根键**：`proxies` / `proxy-providers` 的裸键、双引号、单引号写法（用户 14:41 授权的小修）。
- **Benchmark 用 1 万条**：用户 13:54 定，10 万条仅推算。
- **子代理一次一个、每个最多三个协议**：前一个验收后再卡片批准下一个；brief 必须要求禁止冗余和重复、规范模块化、易维护易阅读；Go 工作先加载 golang-how-to、golang-code-style、golang-modernize、golang-gopls 四个技能。
- **YAML 库用 `go.yaml.in/yaml/v3 v3.0.6`**：v4 仍为 rc；Mihomo v1.19.33 也用 v3。
- **iSH 上测试固定 `GOMAXPROCS=1`、`-p 1`**：多线程曾崩溃 `fatal: bad g in signal handler`。

## 4. 改动文件 (Files)

本轮已提交（`11bb11f`，路径相对仓库根目录）：

- `native-core/internal/towerparse/yaml_scan.go`（新，517 行）：YAML 扫描器，有界单条缓冲、tab 与缩进恢复、flow 集合、块标量、首文档语义、显式跳过原因。主会话修了 `yamlItemWithFinalNewline`，只用于 `emitScannedYAML` 的非 inline 分支。
- `native-core/internal/towerparse/parse.go`：移出 `parseYAML`、`leadingSpaces`、`rootKey`（-147 行）。
- `native-core/internal/towerparse/detect.go`：新增 `yamlRootKeys` 与 `hasYAMLRootKey`。
- `native-core/internal/towerparse/parse_yaml_malformed_test.go`（新，含 `TestDetectQuotedYAMLRootKeys`）。
- `native-core/internal/towerparse/parse_bench_test.go`（新，3 个 Benchmark）。

未提交：

- `native-core/README.md`：+44/-4，内容停在切片 A，已过时。
- `native-core/HANDOFF.md`：本文件。

工作树外：

- `graph.json` 于 14:58 更新（A2d accepted、新增 tg 决定），备份 `/tmp/graph_before_a2d_commit.json`。

## 5. 未决问题 & 阻塞

- **README 过时且有误**：写着 Go 指令 1.23.0（实际 go.mod 为 `go 1.27.0`）、“自行实现，没有复制 SubBoost”（与直接移植决定冲突）、ss/vmess/vless/trojan 未实现。一直未纳入提交；重写还是丢弃待用户决定。
- **LICENSE 未定**：移植代码来自 AGPL-3.0-only。
- **push / 合并 main 未授权**：切片 C 需要 push 才能在 Actions 产出 AAR 与 XCFramework。
- **A2d 未覆盖项**：引号根键冒号前有空格；根级 flow 映射 `{proxies: ...}`；yaml_scan.go 未对照子代理交付逐行复审。
- **已实现协议剩余核查**：Hysteria v1 版本判定、TUIC `fast-open`/`tfo`、XHTTP 复审、超大可选整数受 Go int 限制。上游 TS 未实际运行，对照均为静态阅读。
- **U1 已实施**（2026-11-02 17:20，已提交 `553dd89`）：见文末「U1 实施」节。
- **任务图旧待办**：`pending_main` 里的 Go 升级条目已过时（已装 go1.27.3），尚未清掉。
- **`/tmp` 临时产物**：现有 350 项（脚本、日志、graph 备份、`*.orig`、`*.broken`），清理需用户确认。
- **`docs/context/NOW.md`**：AGENTS.md 第 112 行要求交接时更新。已经用户批准，在进展表加了一行 Go 核心（只改了工作区，未暂存）。合并 main 时可能冲突。

## 6. 下一步 (Next Steps)

- [ ] **由用户选下一项**：切片 C、U1、或先清理并定 LICENSE。入口：graph.json `slices`；派子代理前先卡片批准。
- [ ] **处理 README**：入口 `native-core/README.md`；验证：内容与 go.mod、已实现协议和移植声明一致。需用户决定。
- [ ] **切片 C：gomobile 桥接 + SQLite 驱动**：写入范围 `native-core/`、`.github/workflows/`；验证：Actions 产出 AAR 与 XCFramework。需 push 授权（外部副作用）。
- [ ] **切片 D：SQLite 存储与导入流水线**：批量事务、keyset 分页、分组、proxy-providers 递归拉取；依赖 C。
- [x] **U1 TS 对齐**：已提交 `553dd89`，见文末「U1 实施」节。
- [ ] **剩余核查与去重实现**：见第 5 节与第 3 节 sublinkPro 决定。
- [ ] **清理 `/tmp`**：需用户确认后再删。

## 7. 恢复指引 (How to Resume)

1. `cd /var/minis/workspace/ohmydrift-core`，先读 `CLAUDE.md`、`AGENTS.md`，再读本文件和 graph.json。
2. Go 工作先加载 `/var/minis/skills/golang-how-to/SKILL.md`、`golang-code-style`、`golang-modernize`、`golang-gopls`。导航优先 gopls MCP，再 CLI，最后 rg。MCP 工作区用 `/var/minis/shared/gopls-mcp/gopls-ws <目录>` 切换，当前为 `native-core`；App 重启后未验证。
3. 安全核验（只读加测试；预期 HEAD `6d927d8` 或其后的提交，未提交改动以 `git status --short` 为准）：

        cd /var/minis/workspace/ohmydrift-core
        git status --short --branch
        git log --oneline -6
        cd native-core && export GOROOT=/usr/lib/go GOMAXPROCS=1
        gofmt -l . && go vet ./... && go test -count=1 -p 1 ./...

4. 不要动 `/var/minis/workspace/ohmydrift`（main 工作区，含用户未提交改动和 AGENTS.md 的 Go 规则追加）。
5. commit、push、合并、PR、Release、派子代理、删除 `/tmp` 前都要用户批准。

## 8. 环境 / 约束

- Minis on iOS，iSH Alpine 3.21 aarch64。
- Go `go1.27.3 linux/arm64`（apk `1.27.3-r0`，从 edge community 安装，未改 `/etc/apk/repositories`）；`/usr/bin/go` 指向 `/usr/lib/go`。
- App 环境变量 `GOROOT`（值为 Go 安装目录，用于绕过 iSH `/proc/self/exe` 导致的 GOROOT 检测失败）；`/root/.config/go/env` 中 `GOTOOLCHAIN=auto`。
- 模块 `github.com/Coming404/ohmyfly/native-core`，`go 1.27.0`，唯一依赖 `go.yaml.in/yaml/v3 v3.0.6`。
- gopls `v0.24.0`（`/usr/local/bin/gopls`），MCP 名 `gopls`，启动脚本 `/var/minis/shared/gopls-mcp/start.sh`。
- 子代理最长 60 分钟；App 重启会中断子代理，前台运行较可靠。
- 仓库 `Coming404/ohmyfly`；两个工作树：`ohmydrift`（main `3179012`）与 `ohmydrift-core`（feat/native-core）。未记录任何凭据值。

## 范围决定：不支持平台单行代理配置（2026-11-02 16:33）

- 用户决定：不支持 Surge、Loon、Quantumult X 的单行代理配置，不移植上游 `platform/parse-platform-config.ts`、`platform/parse-platform-proxy-line.ts` 及 `platform/peggy/{surge,loon,qx}.js`。此决定取代 16:21 “移植 platform-config”的批准。
- 偏离：上游注册顺序为 clash-yaml → platform-config → config-lines → link-lines。满足 `!isClashYamlContent && looksLikePlatformConfigContent` 的内容，上游由 platform-config 处理；本地会落到后续路径，节点数、跳过计数与原因与上游不同。
- config-lines 同样不移植（16:34 用户决定：本项目从未支持这类导入）。上游 config-lines 是 Surge 风格的 `名称 = 类型, 地址, 端口, key=value` 单行配置，且每行先尝试 parsePlatformProxyLine。本地移植范围只剩 clash-yaml（整篇加载 + 本地扫描回落）。

## 数字密码类字段修复（2026-11-02 17:02，已提交 `553dd89`）

- 位置：`internal/flight/normalize.go` 的 `mihomoStringScalarFields`、`normalizeStringScalars`、`mihomoStringScalar`、`safeIntegerText`，在 `Normalize` 中确定 type 之后调用（第 26 行），只检查顶层字段。
- 字段集合与上游 `string-scalar.ts` 的 `MIHOMO_STRING_SCALAR_FIELDS` 一致：password、username、psk、auth-str、token、private-key-passphrase、obfs-password。
- 规则对应上游 `normalizeMihomoStringScalar`：字符串原样；安全整数（≤ 2^53−1）转十进制字符串；整值浮点数按 JS number 语义处理（`1.0`→"1"，`-0`→"0"）；其余类型报错，YAML 路径记为 `invalid YAML item`，只跳过该节点。JS 语义为静态推断，上游未运行。
- 测试：`towerparse/parse_yaml_string_scalar_test.go` 的 `TestYAMLStringScalarFields`（10 个 ss 节点，保留 a–e，跳过 f–j）通过；towerparse、flight 分包全量测试通过；`gofmt -l` 无输出。
- 教训：本环境 `file_edit` 与 `file_write` 追加模式曾报告成功但改动未写入（math 导入、调用位置、本节首次追加），每次编辑后先用 `rg` 或 `wc -c` 确认再继续。

## 待办：link 相关既有超限（本次不拆）

- 超 500 行文件：`link_vless_xhttp.go`（930）、`link_vmess.go`（562）。
- 超 80 行函数：`link_anytls.go`、`link_simple_proxy.go`、`link_ss.go`、`link_ssr.go`、`link_vless.go`（2 处）、`link_vless_xhttp.go`、`link_vmess.go`（3 处）及对应测试。完整清单见文末「check_limits 结果（17:24）」。
- YAML 相关文件与 `normalize.go`（425 行）均未超限。

## 用户决定（2026-11-02 17:05）

- link-lines 回落保留：不属于 16:34 的排除范围。入口顺序为 clash-yaml → link-lines（platform-config、config-lines 不移植）。
- U1 按上游：trim 字符集合、linkFormComponent 截断 UTF-8、非法 UTF-8 Base64 三处对齐上游 JS 行为。已于 17:20 实施，见文末「U1 实施」节。

## U1 实施（2026-11-02 17:20，已提交 `553dd89`）

- 新增 `internal/towerparse/link_text.go`：`isJSSpace`/`jsTrimSpace` 使用 JS `String.prototype.trim` 的字符集合（去 U+FEFF、NBSP、U+3000，保留 U+0085）；`removeJSSpace` 对应上游 Base64 的 `/\s/g`，保留非法字节；`decodeUTF8Lossy` 按 WHATWG 最大子段规则替换非法 UTF-8。
- `link_decode.go`：Base64 的 trim 与去空白、`decodeLinkText` 对非法 UTF-8 做替换、`linkFormComponent` 对截断的多字节序列只给一个 U+FFFD、`linkPort` 内联空白判断改用 `isJSSpace`；删除 `unicode` 导入。`link_simple_proxy.go:172` 同样改用 `decodeUTF8Lossy`。
- `strings.TrimSpace` 全部替换为 `jsTrimSpace`：19 个 `link*.go` 共 116 处，加 `parse.go:81` 入口；非测试代码共 120 处调用，函数只在 `link_text.go` 定义一次。`link_query.go` 不再用 `strings`，导入已删。
- 测试：新增 `link_text_test.go`（60 行，4 个测试：`TestJSTrimSpace`、`TestDecodeUTF8Lossy`、`TestLinkSearchParamsTruncatedUTF8`、`TestDecodeLinkBase64JSWhitespace`）。预期值已用本机 node 独立核对：E4 B8、F0 9F 98 各一个 U+FFFD，C0 80 两个，ED A0 80 三个；`URLSearchParams` 对 `%E4%B8` 给一个；trim 去 FEFF、保留 U+0085。
- `parse_yaml_string_scalar_test.go` 删掉调试用的 `t.Logf` 循环。
- 验证（本机 iSH，17:20 前后）：`gofmt -l internal` 无输出；`go vet ./internal/towerparse/ ./internal/flight/` 通过；towerparse、flight 分包全量测试通过。
- 未查：各解析器逐字段的 trim 能否合并到 `linkSearchParams` 等一处。
- 教训：对同一文件并行发出多个 `file_edit` 会互相覆盖，只有一处生效；同一文件的修改要串行，或用脚本按顺序替换并检查旧文本只出现一次。

## check_limits 结果（17:24）

- 脚本 `/var/minis/workspace/towerparse-fix/check_limits.py` 写死只扫 `internal/towerparse/*.go`（含测试），命令行参数不生效；`internal/flight` 未扫描（`normalize.go` 17:02 时 425 行）。完整输出在 `/var/minis/workspace/towerparse-fix/limits.log`。
- 超 500 行文件：`link_vless_xhttp.go`（930）、`link_vmess.go`（562）。
- 超 80 行业务函数（10 个）：`parseAnyTLSLink`（105）、`parseSimpleProxyLink`（95）、`parseSSLink`（86）、`parseSSRLink`（82）、`parseVLESSLink`（238）、`applyVLESSTransport`（87）、`mapXHTTPDownload`（190）、`parseVMessURIConfig`（145）、`buildVMessNode`（156）、`applyVMessTransport`（89）。
- 超 80 行测试函数（8 个）：`TestAnyTLSLinks`（133）、`TestHysteria2Links`（115）、`TestSnellLinks`（109）、`TestTUICLinks`（86）、`TestSubBoostVLESSLinks`（113）、`TestSubBoostVMessLinks`（109）、`TestSubBoostVMessUtils`（84）、`TestWireGuardLinks`（122）。
- 均为既有超限，本次未拆。U1 新增或修改的文件（`link_text.go`、`link_text_test.go`、`link_decode.go` 等）没有超限。

## 提交记录（17:31）

- `f3081de` 添加 AGPL-3.0-only 许可并重写 native-core README（2 文件）。
- `553dd89` towerparse：拆分 YAML 加载与修复，数字密码类字段保留字符串，分享链接 trim 与 UTF-8 解码对齐上游 JS（30 文件，+1330/-364）。
- `6d927d8` 更新交接文档与 NOW.md。
- 提交前 `gofmt -l internal` 无输出，`go vet` 通过；全量测试最后一次运行在 17:20，之后代码未改。未 push。
- 第 4 节「未提交」列表及各节里 17:31 以前的「未提交」字样已过时。
- graph.json 已同步：U1 accepted（553dd89），pending_main 清空；备份 `/var/minis/workspace/towerparse-fix/graph_before_u1_sync.json`。
- 用户决定：字段级 trim 不合并到 `linkSearchParams`。
- 下一步：push 前单独请求授权，运行 C3 Actions workflow 验证 AAR 与 XCFramework；去重（sublinkPro 内容哈希）在 Actions 构建期间或之后做，须在切片 D 前完成。

## C2 SQLite 驱动（2026-11-03 03:40，未提交）

- 新增 `internal/sqlite/sqlite.go`：使用纯 Go `modernc.org/sqlite v1.61.1`，提供带 `context.Context` 的 `Open(ctx, dsn)`，打开后执行 `PingContext`，失败时关闭连接并包装错误。
- 新增 `internal/sqlite/sqlite_test.go`：覆盖内存库、临时文件库、参数化读写、关闭、取消上下文和错误 DSN。
- `go.mod`/`go.sum` 已整理，`go mod verify` 通过；不包含 cgo 驱动。
- 主会话低并发复核：`gofmt`、`go test -p 1 -parallel 1 ./internal/sqlite/`、`go vet -p 1 ./internal/sqlite/`、flight/towerparse 全量测试、mobile 测试、`git diff --check` 均通过。
- `CGO_ENABLED=0 go test ./internal/sqlite/` 在 iSH 编译 `modernc.org/libc` 时因运行环境内存不足失败；Android/iOS 交叉编译留给 C3，不据此判定 C2 代码失败。

## C1 非有限 YAML 值（2026-11-03 02:50，未提交）

- `.nan`、`.inf`、`-.inf` 继续在 `yaml_load.go` 中按 YAML 浮点值解析，不改成字符串；上游 `js-yaml` 也是数值，`JSON.stringify` 会将非有限数输出为 `null`。
- `flight.Normalize` 保留内部字段类型。`flight.Fingerprint` 生成规范化 JSON 副本时把非有限 `float32/float64` 当作 `nil`，保证哈希稳定并与 JSON 表示一致。
- `mobile.ParseSubscription` 仅在 JSON 输出副本中把非有限浮点转为 `null`，不会修改 `Flight.Fields`。
- 新增 mobile 与 flight 回归测试；gofmt、vet、`go test ./mobile/`、`go test ./internal/...` 通过。工作区未提交。
