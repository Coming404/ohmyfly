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
func flight.ContentHash(value flight.Flight) string
```

`Parse` 流式读取并逐条回调，不保存全量指纹集合。`Stats` 记录格式计数、候选/成功/跳过数量、按原因统计的跳过数量和 `proxy-providers` 条目。错误和统计原因只包含行号或协议/原因，不输出原始链接、凭据或指纹。

## 三层去重与正式入库

1. 塔台导入高级规则：`DeduplicationConfig` 的 `mode` 支持 `none`（默认）和 `protocol`，按 `protocolRules` 指定字段稳定保留首个。共享业务方法 `CatalogStore.updateTowerDeduplication` 经 `Catalog(setTowerDedup)` 保存专属规则；下次刷新执行，`none` 不跳过基础内容哈希。
2. 基础 ContentHash：所有解析记录先进入普通／分块暂存表。刷新提交事务逐条计算哈希，完成本批分组，再对每组代表匹配全库。正式 `nodes` 以 `(content_hash, name_key)` 复用，普通节点的 name_key 为空；同一塔台当前旧快照或本次刷新中，同哈希具有多个不同 trim 后名称时按名称区分；每次提交重新判定，例外消失后恢复普通内容复用并迁移全部来源。全部有效来源独立关联，不因重复跳过来源。原生单节点输入也经过同一提交入口；App 当前添加入口接收 HTTP(S) 订阅地址。
3. 汇总航班输出：共享业务方法 `CatalogStore.updateOutputDeduplication` 经 `Catalog(setDedup)` 支持 `none`（默认）、`common`、`protocol`；在基础入库去重后追加字段分组，全局代表先于计数与 100 条键集分页形成。`detail` 返回合并代表的完整配置及全部来源。规则编辑 UI 尚未确定，未新增设置页或塔台 JSON 编辑控件。

规则字段可使用归一化 Mihomo 路径（如 `server`、`password`、`reality-opts.public-key`），并适配上游常用导出字段和嵌套路径（如 `Server`、`Port`、`Param.Password`、`Query.Sni`、`Query.Pbk`）。`commonFields` 另支持 `Name`/`LinkName`、`Protocol`、`LinkHost`、`LinkPort`、`LinkAddress`。协议模式自动带协议前缀；未配置协议／空字段列表不追加去重，全部字段缺失时保留节点，集合字段用规范 JSON 比较。显式 common 规则可以跨协议比较，默认不会按名称删节点。

适配差异：未知属性按语义树参与哈希，不复制上游 YAML AST 行列及注释；字符串数组也识别 JSON 解码的 `[]any`，整数数组保序；规则键使用 JSON 元组，避免 `:`/`|` 拼接冲突。保留内部下划线元数据不参与哈希的本地约定。不复制原始凭据日志、不引入 GORM、测速、DNS 或 GeoIP，也不移植 GetSub 的 EffectiveName 无条件去重。

## 任务与结果持久化

目录现已包含 `flight_tasks`、`node_runs`、`metric_results`、`platform_results`，由 `mobile.Catalog` 暴露创建任务、补成员、状态更新、指标/平台写入、历史与批量摘要读取。Go 与 Kotlin 共用结果字段，平台摘要保留平台标识、状态、地区和 Unix 毫秒时间；成功指标允许0，延迟单位 ms，速度 B/s。单测延迟保留旧速度，失败记录与最近成功值分别提供；取消和重启关闭未完成阶段，完整历史保留。

页面与结果独立修订；任务或结果成功提交后，共享层独立刷新摘要，读取失败不改变写入成功或丢失 task ID。页面/筛选身份变化后补读当前集合。所有列表共享50 MiB估算缓存、每列表最多5批、每批100条。完整 schema、状态、历史游标和 JSON API 见[目录任务与测量结果契约](../docs/catalog-results.md)。当前旧开发 v1 缺少 `metrics_revision` 时拒绝打开并保留文件，升级方案尚未实现；当前实现是持久化契约，网络执行及任务恢复调度属于后续阶段。

## 已实现

- 格式识别：有界前缀按上游根语法分流，支持根级映射、顶层列表、单节点、JSON、带引号或空格的根键。分享链接走独立解析器；Base64 解码后重新识别，最多 3 轮。
- YAML：先整份加载与缩进修复；加载失败或超过 16 MiB 时保留本地逐项恢复扫描器。两条路径共用协议默认值、别名、端点和插件整理；数字凭据通过 `yaml.Node` 保留前导零和大整数原文。
- providers：Go 导出名称、URL 与原始选项，mobile 同时导出节点 ContentHash。Kotlin 自动请求 HTTP provider、解析内嵌 payload；不读取 `type: file` 的外部本地文件。原始订阅为第 0 层，最多展开 3 层，整次导入共用 32 个不同 URL；循环引用停止，同一 URL 不重复请求。原始订阅、provider、UA 尝试和重试共用 `min(timeout×4, 60s)` 时间预算，各响应沿用下载大小限制。Kotlin 只执行导入过滤，基础去重由 SQLite 提交事务完成批内分组与全库匹配；部分失败保留成功节点，并在塔台显示不含 URL/凭据的原因。
- 分享链接（按 scheme 分发）：
  - `vless`（含 Reality、XHTTP）、`vmess` / `vmess1`、`ss`、`ssr`、`trojan`
  - `hysteria` / `hy`、`hysteria2` / `hy2`、`tuic`、`anytls`、`wireguard` / `wg`、`snell`
  - `socks5`、`socks4`、`socks`、`socks5+tls`、`ssh`
  - `tg` 和 t.me 上的 socks/http/https 代理链接；普通 `http(s)` 行跳过，避免把订阅地址当成代理
- 字段规范化：端口数字/字符串统一，域名大小写与尾点归一，IPv4/IPv6 归一，公开字段别名折叠，Reality `short-id` 规范化，WS `ed=<n>` 转为 `max-early-data` 并保留其他 query，未知字段透传。
- SHA-256 内容哈希：`ContentHash` 返回 hex64；按 sublinkPro `Proxy` Go 字段模型投影，忽略 `Name`、`Dialer_proxy`、`Udp`、`Tfo`、`Client_fingerprint`。移除空值、零值和空容器，map 键排序、字符串数组排序、整数数组保序；保留证书 `fingerprint`、`pcs`、`hpkp` 及未知连接字段。下划线内部元数据不参与身份。

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

包含集成测试标签的用例覆盖格式识别、YAML 整理与容错、各协议链接、内容哈希、入库去重、结果生命周期与 mobile JSON 桥接契约。结果回归覆盖部分指标保留、取消/重启、来源删除/合并、平台跨任务最新摘要与历史保留、批量节点隔离及版本/空值/单位时间字段。真实订阅用例从外部 `REVIEW_DATA_DIR` 读取 `source1.yaml` 和 `source2.yaml`，数据不加入仓库；运行 `go test -tags integration ./...`。目录未设置时仅跳过外部数据用例，合成协议、事务和分页用例照常执行。3 个 benchmark 分别用 1 万条 YAML、链接和 Base64 链接。2026-11-06 对固定上游执行 464 项 YAML 差分，452 项一致，剩余 12 项属于上文记录的本地行为或名称契约；公开 Parse 入口与 YAML 入口节点结果一致。VMess 116 项、VLESS 262 项链接差分全部一致。
