// 移植自 SubBoost vmess.ts 的 normalizeVmessTransport 与 buildNodeFromConfig @4015347（AGPL-3.0-only）。
// config 的键名与上游 VMessConfig 一致；来自 JSON 分享链接时，值可能是任意 JSON 类型。
package towerparse

import (
	"cmp"
	"fmt"
	"strconv"
	"strings"
)

func normalizeVMessTransport(rawNet string) (string, bool, error) {
	raw := cmp.Or(strings.ToLower(jsTrimSpace(cmp.Or(rawNet, "tcp"))), "tcp")
	switch raw {
	case "none":
		raw = "tcp"
	case "websocket":
		raw = "ws"
	case "httpupgrade":
		return "ws", true, nil
	}
	switch raw {
	case "tcp", "ws", "grpc", "h2", "http":
		return raw, false, nil
	}
	return "", false, fmt.Errorf("不支持的 VMess 传输层: net=%s（仅支持 tcp/ws/http/h2/grpc/httpupgrade）", cmp.Or(rawNet, "(empty)"))
}

func buildVMessNode(config map[string]any) (map[string]any, error) {
	// 上游 pickString 对非字符串返回空，所以 net 为对象或数字时按 tcp 处理。
	transport, httpUpgrade, err := normalizeVMessTransport(cmp.Or(pickString(config["net"]), "tcp"))
	if err != nil {
		return nil, err
	}
	server, uuid := vmessText(config["add"]), vmessText(config["id"])
	if server == "" || uuid == "" {
		return nil, fmt.Errorf("VMess 配置缺少必要字段")
	}
	port, err := linkPort(vmessText(config["port"]), 0)
	if err != nil || port == 0 {
		return nil, fmt.Errorf("无效的端口号")
	}

	tls := config["tls"] == "tls"
	host, _ := config["host"].(string)
	sni := vmessText(config["sni"])
	if sni == "" && host != "" {
		sni = jsTrimSpace(strings.Split(host, ",")[0])
	}
	node := map[string]any{
		"name":    cmp.Or(vmessText(config["ps"]), "VMess 节点"),
		"type":    "vmess",
		"server":  server,
		"port":    port,
		"uuid":    uuid,
		"alterId": vmessAlterID(config["aid"]),
		"cipher":  cmp.Or(vmessText(config["scy"]), vmessText(config["encryption"]), "auto"),
		"udp":     true,
		"tls":     tls,
	}
	if sni != "" {
		node["servername"] = sni
	}
	if value := cmp.Or(pickString(config["packet-encoding"]), pickString(config["packetEncoding"])); value != "" {
		node["packet-encoding"] = value
	}
	if value, ok := parseBooleanish(vmessFirstDefined(config, "authenticated-length", "authenticatedLength")); ok {
		node["authenticated-length"] = value
	}
	if value, ok := parseBooleanish(vmessFirstDefined(config, "global-padding", "globalPadding")); ok {
		node["global-padding"] = value
	}
	if fp := pickString(config["fp"]); fp != "" {
		node["client-fingerprint"] = fp
	}
	if alpn, ok := config["alpn"].(string); ok {
		addLinkAlpn(node, alpn)
	}
	if insecure, _ := parseBooleanish(config["allowInsecure"]); insecure {
		node["skip-cert-verify"] = true
	}
	if value, ok := config["ech"]; ok {
		if !tls {
			return nil, fmt.Errorf("VMess 启用 ECH 需要 TLS（security=tls）")
		}
		node["ech-opts"] = linkECHOpts(pickString(value))
	}

	frontingSNI := ""
	if tls && sni != server {
		frontingSNI = sni
	}
	applyVMessTransport(node, config, transport, httpUpgrade, host, frontingSNI)
	return node, nil
}

// applyVMessTransport 写入 network 与各传输层选项；net=tcp 加 type=http 在 mihomo 中对应 network=http。
func applyVMessTransport(node, config map[string]any, transport string, httpUpgrade bool, host, frontingSNI string) {
	path, _ := config["path"].(string)
	edge, _ := config["edge"].(string)
	edge = jsTrimSpace(edge)
	httpHosts := splitList(host)
	if httpHosts == nil && frontingSNI != "" {
		httpHosts = []string{frontingSNI}
	}
	ua := ""
	if hasDingTalkHost(httpHosts) {
		ua = dingtalkUserAgent
	}
	if transport == "tcp" && config["type"] == "http" {
		transport = "http"
	}

	switch transport {
	case "ws":
		node["network"] = "ws"
		if opts := vmessWSOpts(path, host, edge, ua, httpUpgrade); opts != nil {
			node["ws-opts"] = opts
		}
	case "h2":
		node["network"] = "h2"
		opts := map[string]any{"path": cmp.Or(path, "/")}
		if hosts := splitList(host); hosts != nil {
			opts["host"] = hosts
		}
		node["h2-opts"] = opts
	case "grpc":
		node["network"] = "grpc"
		opts := map[string]any{"grpc-service-name": cmp.Or(pickString(config["serviceName"]), path)}
		if mode := pickString(config["mode"]); mode != "" {
			opts["_grpcType"] = mode
		}
		if authority := pickString(config["authority"]); authority != "" {
			opts["_grpcAuthority"] = authority
		}
		node["grpc-opts"] = opts
	case "http":
		node["network"] = "http"
		node["http-opts"] = vmessHTTPOpts(config, path, httpHosts, edge, ua)
	default:
		node["network"] = "tcp"
	}
}

// vmessWSOpts 在路径为 / 且没有请求头和早期数据时返回 nil，与上游省略 ws-opts 一致。
func vmessWSOpts(rawPath, host, edge, ua string, httpUpgrade bool) map[string]any {
	path, earlyData := splitLinkEarlyData(rawPath, "/")
	headers := map[string]any{}
	if hosts := splitList(host); hosts != nil {
		headers["Host"] = hosts[0]
	}
	if edge != "" {
		headers["Edge"] = edge
	}
	if ua != "" {
		headers["User-Agent"] = ua
	}
	if path == "/" && len(headers) == 0 && earlyData == 0 {
		return nil
	}
	opts := map[string]any{"path": path}
	if len(headers) > 0 {
		opts["headers"] = headers
	}
	if earlyData > 0 {
		opts["early-data-header-name"] = "Sec-WebSocket-Protocol"
		opts["max-early-data"] = earlyData
	}
	if httpUpgrade {
		opts["v2ray-http-upgrade"] = true
		opts["v2ray-http-upgrade-fast-open"] = true
	}
	return opts
}

func vmessHTTPOpts(config map[string]any, path string, hosts []string, edge, ua string) map[string]any {
	paths := splitList(path)
	if paths == nil {
		paths = []string{"/"}
	}
	opts := map[string]any{"method": normalizeHTTPMethod(config["method"]), "path": paths}
	headers := parseHeaderRecord(config["headers"])
	if headers == nil {
		headers = map[string]any{}
	}
	// 配置里已有的请求头优先，伪装 host、Edge 和钉钉 UA 只补缺失项。
	if _, ok := headers["Host"]; !ok && hosts != nil {
		headers["Host"] = hosts
	}
	if _, ok := headers["Edge"]; !ok && edge != "" {
		headers["Edge"] = []string{edge}
	}
	if _, ok := headers["User-Agent"]; !ok && ua != "" {
		headers["User-Agent"] = []string{ua}
	}
	if len(headers) > 0 {
		opts["headers"] = headers
	}
	return opts
}

// vmessText 返回值在上游 `||` 判断下的文本：字符串原样返回，非零数字按 JS String() 转换，
// 其他类型（含 null）视为缺失。
func vmessText(value any) string {
	switch value := value.(type) {
	case string:
		return value
	case float64:
		if value != 0 {
			return jsNumberString(value)
		}
	}
	return ""
}

// vmessAlterID 对应上游 parseInt(String(aid || 0), 10)；上游得到 NaN 的输入这里取 0。
func vmessAlterID(value any) int {
	alterID, _ := strconv.Atoi(linkIntegerPrefixPattern.FindString(jsTrimSpace(vmessText(value))))
	return alterID
}

// vmessFirstDefined 对应上游 `a ?? b`：跳过缺失和 null 的键。
func vmessFirstDefined(config map[string]any, keys ...string) any {
	for _, key := range keys {
		if value := config[key]; value != nil {
			return value
		}
	}
	return nil
}
