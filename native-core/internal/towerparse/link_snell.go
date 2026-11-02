// Ported from SubBoost packages/core/src/parser/protocols/snell.ts @4015347 (AGPL-3.0-only).
// Ported from SubBoost packages/core/src/mihomo/protocol-fields.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"fmt"
	"strings"
)

func parseSnellLink(raw string) (map[string]any, error) {
	if !strings.HasPrefix(raw, "snell://") {
		return nil, fmt.Errorf("无效的 Snell 链接")
	}
	parsed, query, err := linkURL(raw)
	if err != nil {
		return nil, err
	}
	psk := linkCredential(
		parsed,
		query,
		"psk",
		"password",
		"auth",
		"auth_str",
		"auth-str",
	)
	server := parsed.Hostname
	if psk == "" || server == "" {
		return nil, fmt.Errorf("Snell 配置缺少必要字段")
	}
	port, err := linkPort(parsed.Port, 443)
	if err != nil {
		return nil, fmt.Errorf("无效的端口号")
	}
	node := map[string]any{
		"name": linkName(parsed.Fragment, linkDefaultName(server, port)),
		"type": "snell", "server": server, "port": port, "psk": psk,
	}
	if version, ok := linkIntegerPrefix(query, "version"); ok {
		node["version"] = version
	}
	obfs := strings.ToLower(strings.TrimSpace(query.Get("obfs")))
	if obfs == "http" || obfs == "tls" {
		options := map[string]any{"mode": obfs}
		if host := query.Get("obfs-host"); host != "" {
			options["host"] = host
		}
		if path := query.Get("obfs-uri"); path != "" {
			options["path"] = path
		}
		node["obfs-opts"] = options
	}
	for _, field := range []linkQueryAlias{
		{target: "udp", keys: []string{"udp-relay"}},
		{target: "reuse", keys: []string{"reuse"}},
		{target: "tfo", keys: []string{"fast-open", "tfo"}},
	} {
		if value, ok := linkOptionalBool(query, field.keys...); ok {
			node[field.target] = value
		}
	}
	if _, exists := node["obfs-opts"]; !exists {
		options := map[string]any{"mode": "shadow-tls"}
		if version, ok := linkIntegerPrefix(query, "shadow-tls-version"); ok {
			options["version"] = version
		}
		if host := query.Get("shadow-tls-sni"); host != "" {
			options["host"] = host
		}
		if password := query.Get("shadow-tls-password"); password != "" {
			options["password"] = password
		}
		if len(options) > 1 {
			node["obfs-opts"] = options
		}
	}
	return node, nil
}
