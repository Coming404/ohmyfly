# native-core

OhMyFly 的纯 Go 核心。它读取订阅内容（Clash/Mihomo YAML、逐行分享链接、Base64），把每个节点解析成统一的 `flight.Flight`，再做字段规范化和内容指纹。代码都在 `internal/`。SQLite 存储、网络拉取和 gomobile 桥接尚未开始。

## 模块与依赖

- Go module：`github.com/Coming404/ohmyfly/native-core`
- Go 指令：`go 1.27.0`，本机验证使用 go1.27.3
- YAML：`go.yaml.in/yaml/v3 v3.0.6`（MIT/Apache-2.0）
- 除标准库和上述 YAML 依赖外没有其他依赖

## API

```go
func towerparse.Parse(r io.Reader, emit func(flight.Flight) error) (towerparse.Stats, error)
func towerparse.DetectFormat(prefix []byte) towerparse.Format
func flight.Normalize(input map[string]any) (flight.Flight, error)
func flight.Fingerprint(value flight.Flight) [32]byte
const flight.FingerprintVersion = 1
```

`Parse` 流式读取并逐条回调，不保存全量指纹集合。`Stats` 记录格式计数、候选/成功/跳过数量、按原因统计的跳过数量和 `proxy-providers` 条目。错误和统计原因只包含行号或协议/原因，不输出原始链接、凭据或指纹。

## 已实现

- 格式识别：读开头一段判断 YAML、分享链接或 Base64。YAML 根键 `proxies` / `proxy-providers` 的裸键、双引号、单引号写法都能识别。Base64 支持 URL-safe 字符、缺 padding、换行和百分号编码，最多重新识别 3 轮。
- YAML：流式扫描顶层 `proxies`，单条条目缓冲有上限；支持块式列表项、flow 集合和块标量，只读第一个文档。tab 和缩进错乱的条目会尝试恢复，恢复不了的按原因计入跳过。`proxy-providers` 只提取名称、URL 和其他字段，不拉取。
- 分享链接（按 scheme 分发）：
  - `vless`（含 Reality、XHTTP）、`vmess` / `vmess1`、`ss`、`ssr`、`trojan`
  - `hysteria` / `hy`、`hysteria2` / `hy2`、`tuic`、`anytls`、`wireguard` / `wg`、`snell`
  - `socks5`、`socks4`、`socks`、`socks5+tls`、`ssh`
  - `tg` 和 t.me 上的 socks/http/https 代理链接；普通 `http(s)` 行跳过，避免把订阅地址当成代理
- 字段规范化：端口数字/字符串统一，域名大小写与尾点归一，IPv4/IPv6 归一，公开字段别名折叠，Reality `short-id` 规范化，WS `ed=<n>` 转为 `max-early-data` 并保留其他 query，未知字段透传。
- SHA-256 内容指纹：忽略 `name`、`_meta` 和任意层级下划线开头的字段；名称、端口表示、别名写法不同的等价内容指纹相同。

## 尚未实现

- 去重（计划按 sublinkPro 的内容哈希规则，忽略 name、dialer-proxy、udp、tfo、client-fingerprint）。
- 裸 `IP:端口`、混杂文本提取、proxy-provider 递归拉取、SQLite、网络、gomobile。
- YAML 已知未覆盖：引号根键的冒号前有空格、根级 flow 映射 `{proxies: ...}`。

## 来源与许可

分享链接解析按 SubBoost `401534742802e2b1542783197ce66451bad82739` 逐函数移植，上游许可为 AGPL-3.0-only。每个移植文件的第一行写明上游文件路径和提交。subs-check-pro 只参考了思路，没有复制代码。

与上游有意不同的地方：

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

共 121 个测试函数，覆盖格式识别、YAML 容错、各协议链接与 SubBoost 行为对照、指纹等价规则。3 个 benchmark 分别用 1 万条 YAML、链接和 Base64 链接。
