// Mieru 自定义编辑链接转为 Mihomo 出站字段。
package towerparse

import (
	"fmt"
	"strings"
)

func parseMieruLink(raw string) (map[string]any, error) {
	parsed, query, err := linkURL(raw)
	if err != nil || parsed.Scheme != "mieru" {
		return nil, fmt.Errorf("invalid Mieru URL")
	}
	if parsed.Hostname == "" {
		return nil, fmt.Errorf("Mieru URL 缺少服务器地址")
	}

	node := map[string]any{"type": "mieru", "server": parsed.Hostname}
	fallbackName := parsed.Hostname
	if parsed.Port != "" {
		port, err := linkPort(parsed.Port, 0)
		if err != nil {
			return nil, fmt.Errorf("Mieru URL 端口无效")
		}
		node["port"] = port
		fallbackName += fmt.Sprintf(":%d", port)
	}
	if portRange := linkString(query, "portRange", "port-range"); portRange != "" {
		node["port-range"] = portRange
		if parsed.Port == "" {
			fallbackName += ":" + strings.TrimSpace(portRange)
		}
	}
	node["name"] = linkName(parsed.Fragment, fallbackName)
	if parsed.Username != "" {
		node["username"] = parsed.Username
	}
	if parsed.HasPassword {
		node["password"] = parsed.Password
	}
	if username := linkString(query, "username"); username != "" {
		node["username"] = username
	}
	if password, exists := query["password"]; exists && len(password) > 0 {
		node["password"] = password[0]
	}
	for target, keys := range map[string][]string{
		"transport": {"transport"}, "multiplexing": {"multiplexing"},
		"traffic-pattern": {"trafficPattern", "traffic-pattern"},
	} {
		if value := linkString(query, keys...); value != "" {
			node[target] = value
		}
	}
	return node, nil
}
