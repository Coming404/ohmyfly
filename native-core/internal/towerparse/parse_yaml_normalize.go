// 移植自 SubBoost parser/clash-yaml.ts @4015347（AGPL-3.0-only）。
// YAML 节点的协议默认值、端口与嵌套选项；链接解析不经过此入口。
package towerparse

import (
	"fmt"
	"maps"
	"strings"
)

func normalizeYAMLNode(record map[string]any) error {
	if !yamlJSCoercible(record["type"]) {
		return fmt.Errorf("type cannot be converted to a string")
	}
	typ := strings.ToLower(jsTrimSpace(yamlJSString(record["type"])))
	record["type"] = typ
	defaultYAMLString(record, "name", "未命名节点")
	if typ == "direct" || typ == "dns" || typ == "reject" {
		return nil
	}
	if !yamlJSCoercible(record["network"]) || (typ == "vmess" && !yamlJSCoercible(record["alterId"])) {
		return fmt.Errorf("node value cannot be converted to a primitive")
	}
	normalizeYAMLPorts(record, typ)
	normalizeYAMLWS(record)
	switch typ {
	case "ss":
		defaultYAMLString(record, "cipher", "aes-256-gcm")
		if plugin := record["plugin"]; plugin != nil {
			if _, ok := plugin.(string); !ok {
				return fmt.Errorf("plugin must be a string")
			}
		}
		normalizeYAMLSSPlugin(record)
	case "ssr":
		defaultYAMLString(record, "cipher", "aes-256-cfb")
		defaultYAMLString(record, "protocol", "origin")
		defaultYAMLString(record, "obfs", "plain")
	case "vmess":
		defaultYAMLString(record, "uuid", "")
		defaultYAMLString(record, "cipher", "auto")
		record["alterId"] = yamlJSNumber(record["alterId"])
	case "vless":
		defaultYAMLString(record, "uuid", "")
		normalizeYAMLReality(record)
	case "hy", "hysteria":
		record["type"] = "hysteria"
		defaultYAMLString(record, "protocol", "udp")
	case "hy2":
		record["type"] = "hysteria2"
	case "socks":
		record["type"] = "socks5"
	case "tuic":
		defaultYAMLString(record, "uuid", "")
	}
	switch typ {
	case "ss", "ssr", "trojan", "anytls", "hy2", "hysteria2", "tuic":
		// 数字 0 要先由 Flight 的字符串标量校验转成 "0"；布尔假值不能被默认值掩盖。
		if record["password"] == nil {
			record["password"] = ""
		}
	}
	return nil
}

func normalizeYAMLPorts(record map[string]any, typ string) {
	if typ == "wireguard" {
		if peers, ok := record["peers"].([]any); ok {
			cloned := append([]any(nil), peers...)
			for i, peer := range peers {
				if fields, ok := yamlMapping(peer); ok {
					fields = maps.Clone(fields)
					setYAMLPort(fields)
					cloned[i] = fields
				}
			}
			record["peers"] = cloned
		}
	}
	setYAMLPort(record)
	if typ != "hy2" && typ != "hysteria2" && typ != "hy" && typ != "hysteria" {
		return
	}
	ports := yamlPortsSpec(record["ports"])
	delete(record, "ports")
	if ports == "" {
		return
	}
	record["ports"] = ports
	if (typ == "hy2" || typ == "hysteria2") && record["port"] == nil {
		record["port"] = linkStablePortFromPorts(ports)
	}
}

func normalizeYAMLReality(record map[string]any) {
	opts, ok := yamlObjectSpread(record["reality-opts"])
	delete(record, "reality-opts")
	if !ok {
		return
	}
	if key, ok := opts["public-key"].(string); ok {
		opts["public-key"] = jsTrimSpace(key)
	}
	id := ""
	switch value := opts["short-id"].(type) {
	case string:
		id = value
	case int, int64, uint64, float64:
		id = yamlJSString(value)
	}
	id = yamlRealityShortID(id)
	delete(opts, "short-id")
	if id != "" {
		opts["short-id"] = id
	}
	if len(opts) != 0 {
		record["reality-opts"] = opts
	}
}

func normalizeYAMLWS(record map[string]any) {
	if yamlJSString(record["network"]) != "ws" {
		return
	}
	opts, ok := yamlMapping(record["ws-opts"])
	if !ok {
		return
	}
	path, _ := opts["path"].(string)
	path, early := splitLinkEarlyData(jsTrimSpace(path), "/")
	if early == 0 {
		return
	}
	opts["path"] = path
	_, hasMax := opts["max-early-data"]
	_, hasHeader := opts["early-data-header-name"]
	if !hasMax && !hasHeader {
		opts["max-early-data"] = early
		opts["early-data-header-name"] = "Sec-WebSocket-Protocol"
	}
}

// 先去一次前缀，再去内嵌空白；不能把 "0 xab" 或 "0x0Xab" 当成合法前缀。
func yamlRealityShortID(value string) string {
	value = jsTrimSpace(value)
	if strings.HasPrefix(value, "0x") || strings.HasPrefix(value, "0X") {
		value = value[2:]
	}
	value = strings.NewReplacer(":", "", "_", "", "-", "").Replace(removeJSSpace(value))
	if value == "" || len(value) > 16 || !isHexString(value) {
		return ""
	}
	if len(value)%2 == 1 {
		value = "0" + value
	}
	return strings.ToLower(value)
}
