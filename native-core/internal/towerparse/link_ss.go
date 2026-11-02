// Ported from SubBoost packages/core/src/parser/protocols/ss.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"encoding/json"
	"fmt"
	"strings"
)

func parseSSLink(raw string) (map[string]any, error) {
	raw = normalizeLinkScheme(raw, "ss")
	content := strings.TrimPrefix(raw, "ss://")
	fragment := ""
	if index := strings.IndexByte(content, '#'); index >= 0 {
		fragment = content[index+1:]
		content = content[:index]
	}
	queryString := ""
	if index := strings.IndexByte(content, '?'); index >= 0 {
		queryString = content[index+1:]
		content = content[:index]
	}
	query := linkSearchParams(queryString)
	var err error
	name := linkName(fragment, "")
	var cipher, password, server string
	var port int
	atIndex := strings.LastIndexByte(content, '@')
	if atIndex >= 0 {
		userinfo := linkDecodeComponent(content[:atIndex])
		serverPart := content[atIndex+1:]
		server, port, err = splitSSServerPort(serverPart)
		if err != nil {
			return nil, err
		}
		decoded := userinfo
		if !strings.Contains(decoded, ":") {
			decoded, err = decodeLinkText(decoded)
			if err != nil {
				return nil, err
			}
		}
		cipher, password, err = splitCipherPassword(decoded)
		if err != nil {
			return nil, err
		}
	} else {
		decoded, decodeErr := decodeLinkText(content)
		if decodeErr != nil {
			return nil, decodeErr
		}
		atIndex = strings.LastIndexByte(decoded, '@')
		if atIndex < 0 {
			return nil, fmt.Errorf("invalid ss payload")
		}
		cipher, password, err = splitCipherPassword(decoded[:atIndex])
		if err != nil {
			return nil, err
		}
		server, port, err = splitSSServerPort(decoded[atIndex+1:])
		if err != nil {
			return nil, err
		}
	}
	if name == "" {
		name = linkDefaultName(server, port)
	}
	node := map[string]any{
		"name":     name,
		"type":     "ss",
		"server":   server,
		"port":     port,
		"cipher":   cipher,
		"password": password,
		"udp":      true,
	}
	if plugin := query.Get("plugin"); plugin != "" {
		addSSPlugin(node, plugin)
	} else if encoded := query.Get("v2ray-plugin"); encoded != "" {
		if decoded, decodeErr := decodeLinkText(encoded); decodeErr == nil {
			var options map[string]any
			if json.Unmarshal([]byte(decoded), &options) == nil {
				node["plugin"] = "v2ray-plugin"
				node["plugin-opts"] = normalizeSSPluginOptions(options)
			}
		}
	}
	if value, ok := linkBoolValue(query, "uot"); ok && value {
		node["udp-over-tcp"] = true
	}
	if value, ok := linkBoolValue(query, "tfo"); ok && value {
		node["tfo"] = true
	}
	return node, nil
}

func splitCipherPassword(value string) (string, string, error) {
	index := strings.IndexByte(value, ':')
	if index < 0 {
		return "", "", fmt.Errorf("invalid ss credentials")
	}
	return value[:index], value[index+1:], nil
}

// splitSSServerPort mirrors upstream ss.ts: split on the last ":", parseInt
// the port, and strip IPv6 brackets only when the host is fully wrapped.
// Empty hosts are accepted, matching upstream.
func splitSSServerPort(value string) (string, int, error) {
	index := strings.LastIndexByte(value, ':')
	if index < 0 {
		return "", 0, fmt.Errorf("missing ss port")
	}
	port, err := linkPort(value[index+1:], 0)
	if err != nil || port == 0 {
		return "", 0, fmt.Errorf("invalid ss port")
	}
	server := value[:index]
	if len(server) >= 2 && server[0] == '[' && server[len(server)-1] == ']' {
		server = server[1 : len(server)-1]
	}
	return server, port, nil
}

func addSSPlugin(node map[string]any, raw string) {
	parts := splitEscapedPlugin(raw)
	if len(parts) == 0 || strings.TrimSpace(parts[0]) == "" {
		return
	}
	plugin := strings.TrimSpace(parts[0])
	opts := map[string]any{}
	for _, part := range parts[1:] {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		key, value, found := strings.Cut(part, "=")
		key = strings.TrimSpace(key)
		if key == "" {
			continue
		}
		if !found || strings.TrimSpace(value) == "" {
			opts[key] = true
		} else {
			opts[key] = strings.TrimSpace(value)
		}
	}
	plugin, normalized := normalizeSSPlugin(plugin, opts)
	if plugin == "" {
		return
	}
	node["plugin"] = plugin
	if len(normalized) > 0 {
		node["plugin-opts"] = normalized
	}
}

func splitEscapedPlugin(value string) []string {
	var result []string
	var builder strings.Builder
	escaped := false
	for _, char := range value {
		if escaped {
			if char == ';' || char == '=' || char == '\\' {
				builder.WriteRune(char)
			} else {
				builder.WriteByte('\\')
				builder.WriteRune(char)
			}
			escaped = false
			continue
		}
		if char == '\\' {
			escaped = true
			continue
		}
		if char == ';' {
			result = append(result, builder.String())
			builder.Reset()
			continue
		}
		builder.WriteRune(char)
	}
	if escaped {
		builder.WriteByte('\\')
	}
	return append(result, builder.String())
}

func normalizeSSPluginOptions(options map[string]any) map[string]any {
	result := map[string]any{}
	for key, value := range options {
		if key == "mux" || key == "tls" {
			if boolValue, ok := boolField(map[string]any{"value": value}, "value"); ok {
				result[key] = boolValue
				continue
			}
		}
		result[key] = value
	}
	return result
}
