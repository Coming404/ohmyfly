// 按 SubBoost 4015347 的 YAML 端点契约构建 Flight，避免再次改写已处理的传输字段。
package flight

import (
	"fmt"
	"regexp"
	"strconv"
	"strings"
)

var yamlMieruRange = regexp.MustCompile(`^([0-9]{1,5})-([0-9]{1,5})$`)

// NormalizeYAML 接收已完成协议默认值、端口、Reality 和 WS 整理的节点。
func NormalizeYAML(input map[string]any) (Flight, error) {
	node := cloneValue(input).(map[string]any)
	typ, ok := node["type"].(string)
	if !ok || typ == "" {
		return Flight{}, fmt.Errorf("missing type")
	}
	if err := normalizeStringScalars(node); err != nil {
		return Flight{}, err
	}
	if err := validateYAMLEndpoint(node, typ); err != nil {
		return Flight{}, err
	}
	name, _ := stringValue(node["name"])
	delete(node, "name")
	server, _ := node["server"].(string)
	port, _ := portValue(node["port"])
	if typ != "direct" && typ != "dns" && typ != "reject" && typ != "relay" && server != "" {
		server = normalizeServer(server)
		node["server"] = server
	}
	canonicalizeYAMLAliases(node, typ)
	if err := normalizeYAMLProtocolFields(node, typ); err != nil {
		return Flight{}, err
	}
	if typ == "ssh" {
		if err := validateSSH(node); err != nil {
			return Flight{}, err
		}
	}
	return Flight{Name: name, Type: typ, Server: server, Port: port, Fields: node}, nil
}

func validateYAMLEndpoint(node map[string]any, typ string) error {
	switch typ {
	case "direct", "dns", "reject", "relay":
		return nil
	case "wireguard":
		if raw, exists := node["peers"]; exists {
			if err := validateYAMLPeers(raw); err != nil {
				return err
			}
			_, hasServer := node["server"]
			_, hasPort := node["port"]
			if !hasServer && !hasPort {
				return nil
			}
		}
	}
	server, ok := node["server"].(string)
	if !ok || strings.TrimSpace(server) == "" {
		return fmt.Errorf("missing server")
	}
	if value, exists := node["port-range"]; typ == "mieru" && exists {
		if _, exists := node["port"]; exists {
			return fmt.Errorf("mieru port conflicts with port-range")
		}
		text, _ := value.(string)
		parts := yamlMieruRange.FindStringSubmatch(text)
		if len(parts) == 3 {
			start, _ := strconv.Atoi(parts[1])
			end, _ := strconv.Atoi(parts[2])
			if start >= 1 && start <= end && end <= 65535 {
				return nil
			}
		}
		return fmt.Errorf("invalid mieru port-range")
	}
	if port, ok := portValue(node["port"]); !ok || port < 1 || port > 65535 {
		return fmt.Errorf("invalid port")
	}
	return nil
}

func validateYAMLPeers(value any) error {
	peers, ok := value.([]any)
	if !ok || len(peers) == 0 {
		return fmt.Errorf("wireguard peers must be a non-empty array")
	}
	for _, raw := range peers {
		peer, ok := raw.(map[string]any)
		if !ok {
			return fmt.Errorf("invalid wireguard peer")
		}
		server, serverOK := peer["server"].(string)
		key, keyOK := peer["public-key"].(string)
		port, portOK := portValue(peer["port"])
		if !serverOK || strings.TrimSpace(server) == "" || !keyOK || strings.TrimSpace(key) == "" || !portOK || port < 1 || port > 65535 {
			return fmt.Errorf("wireguard peer missing server, port or public-key")
		}
	}
	return nil
}
