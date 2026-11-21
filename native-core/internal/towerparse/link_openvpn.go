// OpenVPN 自定义编辑链接转为 Mihomo 出站字段。
package towerparse

import (
	"fmt"
	"strconv"
	"strings"
)

const defaultOpenVPNLinkPort = 1194

func parseOpenVPNLink(raw string) (map[string]any, error) {
	parsed, query, err := linkURL(raw)
	if err != nil || parsed.Scheme != "openvpn" {
		return nil, fmt.Errorf("invalid OpenVPN URL")
	}
	if parsed.Hostname == "" {
		return nil, fmt.Errorf("OpenVPN URL 缺少服务器地址")
	}
	port, err := linkPort(parsed.Port, defaultOpenVPNLinkPort)
	if err != nil {
		return nil, fmt.Errorf("OpenVPN URL 端口无效")
	}
	node := map[string]any{
		"type": "openvpn", "server": parsed.Hostname, "port": port,
		"name": linkName(parsed.Fragment, fmt.Sprintf("%s:%d", parsed.Hostname, port)),
	}
	for _, key := range []string{
		"proto", "dev", "cipher", "data-ciphers-fallback", "auth", "comp-lzo",
		"ca", "cert", "key", "tls-auth", "key-direction", "tls-crypt", "tls-crypt-v2",
		"username", "password", "interface-name", "ip-version", "dialer-proxy",
	} {
		if values, exists := query[key]; exists && len(values) > 0 {
			node[key] = values[0]
		}
	}
	for _, key := range []string{"data-ciphers", "dns"} {
		if values, exists := query[key]; exists && len(values) > 0 {
			node[key] = values
		}
	}
	for _, key := range []string{"ping", "ping-restart", "tran-window", "handshake-timeout", "mtu", "routing-mark"} {
		if values, exists := query[key]; exists && len(values) > 0 {
			value, parseErr := strconv.Atoi(strings.TrimSpace(values[0]))
			if parseErr != nil {
				return nil, fmt.Errorf("OpenVPN URL %s 无效", key)
			}
			node[key] = value
		}
	}
	for _, key := range []string{"udp", "remote-dns-resolve", "tfo", "mptcp"} {
		if _, exists := query[key]; !exists {
			continue
		}
		value, ok := linkBoolValue(query, key)
		if !ok {
			return nil, fmt.Errorf("OpenVPN URL %s 无效", key)
		}
		node[key] = value
	}
	for _, key := range []string{"peer-info", "ip-stack"} {
		if value := linkString(query, key); value != "" {
			object, ok := parseJSONObject(value)
			if !ok {
				return nil, fmt.Errorf("OpenVPN URL %s 必须是 JSON 对象", key)
			}
			node[key] = object
		}
	}
	return node, nil
}
