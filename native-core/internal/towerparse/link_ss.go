// Ported from SubBoost packages/core/src/parser/protocols/ss.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"encoding/json"
	"fmt"
	"net/url"
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
	query, err := url.ParseQuery(queryString)
	if err != nil {
		return nil, err
	}
	name := linkName(fragment, "")
	var cipher, password, server string
	var port int
	atIndex := strings.LastIndexByte(content, '@')
	if atIndex >= 0 {
		userinfo, decodeErr := url.PathUnescape(content[:atIndex])
		if decodeErr != nil {
			return nil, decodeErr
		}
		serverPart := content[atIndex+1:]
		server, port, err = splitLinkServerPort(serverPart, 0)
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
		server, port, err = splitLinkServerPort(decoded[atIndex+1:], 0)
		if err != nil {
			return nil, err
		}
	}
	if server == "" || port < 1 || port > 65535 {
		return nil, fmt.Errorf("invalid ss server or port")
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
	if index <= 0 {
		return "", "", fmt.Errorf("invalid ss credentials")
	}
	return value[:index], value[index+1:], nil
}

func splitLinkServerPort(value string, fallback int) (string, int, error) {
	value = strings.TrimSuffix(strings.TrimSpace(value), "/")
	if strings.HasPrefix(value, "[") {
		end := strings.IndexByte(value, ']')
		if end < 0 {
			return "", 0, fmt.Errorf("invalid server")
		}
		server := value[1:end]
		port, err := linkPort(strings.TrimPrefix(value[end+1:], ":"), fallback)
		if err != nil {
			return "", 0, err
		}
		return server, port, nil
	}
	index := strings.LastIndexByte(value, ':')
	if index <= 0 {
		return "", 0, fmt.Errorf("missing port")
	}
	port, err := linkPort(value[index+1:], fallback)
	if err != nil {
		return "", 0, err
	}
	return value[:index], port, nil
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
