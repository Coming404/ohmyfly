// 移植自 SubBoost packages/core/src/parser/protocols/trojan.ts @4015347（AGPL-3.0-only）。
// 格式：trojan://password@server:port?params#name。
// 有意偏离：上游忽略 pcs/hpkp，这里按 addLinkPinnedCertificate 写入 fingerprint 并保留原始 pcs。
package towerparse

import (
	"cmp"
	"fmt"
	"net/url"
	"strings"
)

func parseTrojanLink(raw string) (map[string]any, error) {
	raw = normalizeLinkScheme(raw, "trojan")
	if !strings.HasPrefix(raw, "trojan://") {
		return nil, fmt.Errorf("无效的 Trojan 链接")
	}
	parsed, query, err := linkURL(raw)
	if err != nil {
		return nil, err
	}
	// 密码里未编码的冒号会被拆成 username/password，这里拼回去。
	password := parsed.Username
	if parsed.Password != "" {
		password += ":" + parsed.Password
	}
	server := parsed.Hostname
	if password == "" || server == "" {
		return nil, fmt.Errorf("Trojan 配置缺少必要字段")
	}
	port, err := linkPort(parsed.Port, 443)
	if err != nil {
		return nil, fmt.Errorf("无效的端口号")
	}

	node := map[string]any{
		"name":     cmp.Or(linkDecodeForm(parsed.Fragment), "Trojan 节点"),
		"type":     "trojan",
		"server":   server,
		"port":     port,
		"password": password,
		"udp":      true,
		// mihomo 的 Trojan 适配器只读 sni（adapter/outbound/trojan.go:52）。
		"sni": cmp.Or(pickQueryParam(query, "sni", "peer"), server),
	}
	if values, ok := query["ech"]; ok {
		node["ech-opts"] = linkECHOpts(jsTrimSpace(values[0]))
	}
	for _, key := range []string{"allowInsecure", "allow_insecure", "allow-insecure", "insecure"} {
		if value, _ := linkBool(query.Get(key)); value {
			node["skip-cert-verify"] = true
		}
	}
	if fp := jsTrimSpace(pickQueryParam(query, "fp", "fingerprint", "client-fingerprint", "clientFingerprint")); fp != "" {
		node["client-fingerprint"] = fp
	}
	addLinkAlpn(node, query.Get("alpn"))
	addLinkPinnedCertificate(node, query)
	applyTrojanTransport(node, query)
	return node, nil
}

// applyTrojanTransport 对应上游 switch(type)：只认 ws、httpupgrade、grpc，其余一律按 tcp。
func applyTrojanTransport(node map[string]any, query url.Values) {
	ws := query.Get("ws")
	transport := query.Get("type")
	if transport == "" {
		transport = "tcp"
		if ws == "1" || ws == "true" {
			transport = "ws"
		}
	}
	transport = strings.ToLower(jsTrimSpace(transport))
	path := cmp.Or(pickQueryParam(query, "path", "wspath", "ws-path"), "/")
	host := pickQueryParam(query, "host", "obfsParam", "obfs-host")

	switch transport {
	case "ws", "httpupgrade":
		node["network"] = "ws"
		wsPath, earlyData := splitLinkEarlyData(path, "/")
		opts := map[string]any{"path": wsPath}
		if host != "" {
			opts["headers"] = map[string]any{"Host": host}
		}
		if earlyData > 0 {
			opts["early-data-header-name"] = "Sec-WebSocket-Protocol"
			opts["max-early-data"] = earlyData
		}
		if transport == "httpupgrade" {
			opts["v2ray-http-upgrade"] = true
			opts["v2ray-http-upgrade-fast-open"] = true
		}
		node["ws-opts"] = opts
	case "grpc":
		node["network"] = "grpc"
		opts := map[string]any{"grpc-service-name": cmp.Or(pickQueryParam(query, "serviceName", "service_name", "service-name"), strings.TrimPrefix(path, "/"))}
		if mode := query.Get("mode"); mode != "" {
			opts["_grpcType"] = mode
		}
		if authority := query.Get("authority"); authority != "" {
			opts["_grpcAuthority"] = authority
		}
		node["grpc-opts"] = opts
	default:
		node["network"] = "tcp"
	}
}
