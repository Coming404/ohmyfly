# native-core

OhMyFly 的纯 Go 核心。它读取订阅内容（Clash/Mihomo YAML、逐行分享链接、Base64），把每个节点解析成统一的 `flight.Flight`，再做字段规范化和内容指纹。解析实现位于 `internal/`，通过 `mobile.ParseSubscription` 的 JSON 结果交给 App；订阅与 provider 的网络请求由 Kotlin 完成。

## 模块与依赖

- Go module：`github.com/Coming404/ohmyfly/native-core`
- Go 指令：`go 1.27.0`，本机验证使用 go1.27.3
- YAML：`go.yaml.in/yaml/v3 v3.0.6`（MIT/Apache-2.0）
- SQLite：`modernc.org/sqlite v1.61.1`；完整依赖见 `go.mod`。
- 正式 YAML 解析只使用 `yaml/v3`，不引入 `js-yaml` 或 JavaScript 运行时。上游 TS/js-yaml 仅用于仓库外的差分对照工具。

## API

```go
func towerparse.Parse(r io.Reader, emit func(flight.Flight) error) (towerparse.Stats, error)
func towerparse.DetectFormat(prefix []byte) towerparse.Format
func flight.Normalize(input map[string]any) (flight.Flight, error)
// YAML 协议字段整理后调用；不会再次处理 Reality 和 WebSocket 参数。
func flight.NormalizeYAML(input map[string]any) (flight.Flight, error)
func flight.Fingerprint(value flight.Flight) [32]byte
const flight.FingerprintVersion = 1
```

`Parse` 流式读取并逐条回调，不保存全量指纹集合。`Stats` 记录格式计数、候选/成功/跳过数量、按原因统计的跳过数量和 `proxy-providers` 条目。错误和统计原因只包含行号或协议/原因，不输出原始链接、凭据或指纹。

## 已实现

- 格式识别：有界前缀按上游根语法分流，支持根级映射、顶层列表、单节点、JSON、带引号或空格的根键。分享链接走独立解析器；Base64 解码后重新识别，最多 3 轮。
- YAML：先整份加载与缩进修复；加载失败或超过 16 MiB 时保留本地逐项恢复扫描器。两条路径共用协议默认值、别名、端点和插件整理；数字凭据通过 `yaml.Node` 保留前导零和大整数原文。
- providers：Go 导出名称、URL 与原始选项，mobile 同时导出节点内容指纹。Kotlin 自动请求 HTTP provider、解析内嵌 payload；不读取 `type: file` 的外部本地文件。原始订阅为第 0 层，最多展开 3 层，整次导入共用 32 个不同 URL；循环引用停止，同一 URL 不重复请求。原始订阅、provider、UA 尝试和重试共用 `min(timeout×4, 60s)` 时间预算，各响应沿用下载大小限制。过滤后按内容指纹去重；部分失败保留成功节点，并在塔台显示不含 URL/凭据的原因。
- 分享链接（按 scheme 分发）：
  - `vless`（含 Reality、XHTTP）、`vmess` / `vmess1`、`ss`、`ssr`、`trojan`
  - `hysteria` / `hy`、`hysteria2` / `hy2`、`tuic`、`anytls`、`wireguard` / `wg`、`snell`
  - `socks5`、`socks4`、`socks`、`socks5+tls`、`ssh`
  - `tg` 和 t.me 上的 socks/http/https 代理链接；普通 `http(s)` 行跳过，避免把订阅地址当成代理
- 字段规范化：端口数字/字符串统一，域名大小写与尾点归一，IPv4/IPv6 归一，公开字段别名折叠，Reality `short-id` 规范化，WS `ed=<n>` 转为 `max-early-data` 并保留其他 query，未知字段透传。
- SHA-256 内容指纹：忽略 `name`、`_meta` 和任意层级下划线开头的字段；名称、端口表示、别名写法不同的等价内容指纹相同。

## 尚未实现

- 裸 `IP:端口`、混杂文本提取。
- provider 的持久化缓存、定时更新，以及依赖 Clash 本地运行环境的选项执行，不属于本次订阅展开。
- 本次新增 Kotlin provider 测试尚未在 Actions 编译运行；代码完成不代表双端运行验证通过。

## 来源与许可

分享链接解析按 SubBoost `401534742802e2b1542783197ce66451bad82739` 逐函数移植，上游许可为 AGPL-3.0-only。每个移植文件的第一行写明上游文件路径和提交。subs-check-pro 只参考了思路，没有复制代码。

输出的字段名和含义以 mihomo v1.19.33 的适配器（`adapter/outbound/*.go`）为准，上游只决定接受哪些链接格式。两者冲突时按 mihomo 处理。

与上游有意不同的地方：

- YAML 保留逐项恢复扫描器及其错误计数方式；上游整份失败时不恢复。App 接收并拉取 provider，因此不追加上游“浏览器无法拉取 provider”的错误。
- YAML 的域名大小写、尾点、IPv6 写法沿用本地统一规则；`Flight.Name` 与 App 契约为字符串，上游接受的非字符串名称在本地为空。其余已覆盖的 YAML 协议整理按固定上游执行，链接解析不进入 YAML 专用入口。

- VLESS、Trojan、AnyTLS 的 `pcs`（Xray 的证书 SHA-256 固定）同时写两个字段。`fingerprint` 取 `pcs` 或 `hpkp` 中第一个 64 位十六进制值，供 mihomo 校验证书，与 mihomo 自带的链接转换（`common/convert/v.go:39`、`converter.go:216`、`converter.go:620`）一致。`pcs` 原样保留，导出分享链接时写回，不丢失逗号分隔的多个指纹和原始写法。上游 VLESS、AnyTLS 只保留 `pcs`，Trojan 两者都不写；mihomo 适配器没有 `pcs` 字段，只靠它时，服务器用自签证书的节点会因证书校验失败而无法连接。`fingerprint` 只能放一个值是 mihomo 的限制，多个指纹时只按第一个有效值校验。
- 大写协议名（如 `TROJAN://`）在分发前统一转成小写，与上游 `normalizeNodeLinkScheme` 一致；直接调用上游 `parseTrojan`、`parseAnyTLS` 时会被拒绝。
- Hysteria、Hysteria2 的 `up`、`down` 改写成 mihomo 能读取的形式。mihomo 的 `StringToBps`（`common/utils/mbps.go`）只接受纯数字或 `^\d+\s*[KMGT]?[Bb]ps$`，单位前缀必须大写。上游给 Hysteria2 纯数字补的 ` mbps` 和原样保留的 `1gbps` 都会被读成 0：Hysteria2 节点不限速，Hysteria 节点加载时报错。本地保留 mihomo 已能读取的写法，其余按 Hysteria 官方单位（`b`、`k`、`kb`、`kbps` 等，不区分大小写）改写，如 `1gbps` 改成 `1 Gbps`；Hysteria2 的纯数字补 ` Mbps`，Hysteria 的纯数字保持不变；无法识别的写法原样保留。
- Hysteria2 的 `fp`、`fingerprint`、`pinSHA256` 只取第一个 SHA-256 写成小写十六进制的 `fingerprint`。上游原样写入，再由 `proxy-sanitizer` 删掉非证书值；本地没有这一步，`fp=chrome` 这类值会让 mihomo 生成 TLS 配置时报错，节点无法加载。
- WireGuard 链接中未单独处理的参数按 mihomo `WireGuardOption` 的字段类型写入：`dns`、`allowed-ips` 按逗号拆成列表，`tfo`、`mptcp`、`remote-dns-resolve` 转成布尔值，无法转换时丢弃。上游原样写字符串，mihomo 解码这些字段时报错，节点无法加载。
- AnyTLS、TUIC 的整数参数超出 Go `int` 范围时丢弃，上游 `parseInt` 会得到 `1e20` 这类 mihomo 无法读取的值。
- SOCKS 链接的默认名统一用 `SOCKS-host:port`，只在生成默认名时决定前缀。上游 `parseSocks` 在解析后把等于 `SOCKS5-host:port` 的名称一律改成 `SOCKS-`，用户在 `#` 里显式写的这个名称也会被改；本地保留用户写的名称。
- SSH 节点（链接和 YAML）缺 `username`，或 `password` 和 `private-key` 都没有时计入跳过（`flight/validate_ssh.go`）。mihomo 的 `username` 是必填字段，缺少时加载报错；上游的有效性检查不查 `username`，只在生成配置时要求至少有一种认证。接入 mihomo 后改用 `adapter.ParseProxy` 检查，届时删除这段手写检查。

- Base64 解码遇到非法字符时报错，与浏览器 `atob` 一致。上游在 Node 下用 `Buffer` 解码，会跳过非法字符继续解码。因此 SSR 参数含非法 `%`（如 `remarks=100%`）时保留原文，上游在 Node 下得到乱码；SS 的 `v2ray-plugin` 直接写 JSON 时能识别插件，上游在 Node 下会丢掉插件。VMess JSON 的 Base64 中间夹非法字符时整条拒绝，上游在 Node 下仍能解析。
- VMess JSON 字段类型不对时按字段需要的类型处理。`id`、`add`、`sni` 为数字时转成字符串，为对象时视为缺失；`alpn` 为数组时忽略。上游会把数字或对象原样写进 `uuid`、`server`、`servername`，`alpn` 为数组时调用 `split` 报错。

native-core 随仓库整体按 GNU Affero General Public License v3.0 only（`AGPL-3.0-only`）发布，全文见仓库根目录 [LICENSE](../LICENSE)。依赖 `go.yaml.in/yaml/v3` 保留其 MIT/Apache-2.0 许可。

## 测试

iSH 上多线程运行曾出现 `fatal: bad g in signal handler`，所以固定单线程：

```sh
cd native-core
export GOMAXPROCS=1
gofmt -l .
go vet ./...
go test -count=1 -p 1 ./...
go test -run '^$' -bench . -benchtime=1x ./internal/towerparse
```

共 154 个 Go 测试函数，覆盖格式识别、YAML 整理与容错、各协议链接、内容指纹和 mobile provider 契约。3 个 benchmark 分别用 1 万条 YAML、链接和 Base64 链接。2026-11-06 对固定上游执行 464 项 YAML 差分，452 项一致，剩余 12 项属于上文记录的本地行为或名称契约；公开 Parse 入口与 YAML 入口节点结果一致。VMess 116 项、VLESS 262 项链接差分全部一致。
