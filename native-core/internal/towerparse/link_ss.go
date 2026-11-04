// Ported from SubBoost packages/core/src/parser/protocols/ss.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"cmp"
	"encoding/json"
	"fmt"
	"maps"
	"net/url"
	"strings"
)

func parseSSLink(raw string) (map[string]any, error) {
	content := strings.TrimPrefix(raw, "ss://")
	// 与上游一致：没有 # 时叫 "SS 节点"；有 # 时直接取解码结果，可以为空。
	name := "SS 节点"
	if index := strings.IndexByte(content, '#'); index >= 0 {
		name = linkDecodeForm(content[index+1:])
		content = content[:index]
	}
	queryString := ""
	if index := strings.IndexByte(content, '?'); index >= 0 {
		queryString = content[index+1:]
		content = content[:index]
	}
	query := linkSearchParams(queryString)
	var err error
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
	node := map[string]any{
		"name":     name,
		"type":     "ss",
		"server":   server,
		"port":     port,
		"cipher":   cipher,
		"password": password,
		"udp":      true,
	}
	addSSQueryPlugin(node, query)
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

// addSSQueryPlugin mirrors upstream parseSS: v2ray-plugin JSON (Base64 or raw)
// is used only when the plugin parameter yields no plugin name.
func addSSQueryPlugin(node map[string]any, query url.Values) {
	if plugin := query.Get("plugin"); plugin != "" {
		addSSPlugin(node, plugin)
	}
	if _, ok := node["plugin"]; ok {
		return
	}
	encoded := jsTrimSpace(query.Get("v2ray-plugin"))
	if encoded == "" {
		return
	}
	decoded, err := decodeLinkText(encoded)
	if err != nil {
		decoded = encoded
	}
	var options map[string]any
	if json.Unmarshal([]byte(decoded), &options) != nil || options == nil {
		return
	}
	node["plugin"] = "v2ray-plugin"
	node["plugin-opts"] = normalizeSSPluginOptions(options)
}

func addSSPlugin(node map[string]any, raw string) {
	parts := splitEscapedPlugin(raw)
	if len(parts) == 0 || jsTrimSpace(parts[0]) == "" {
		return
	}
	plugin := jsTrimSpace(parts[0])
	opts := map[string]any{}
	for _, part := range parts[1:] {
		part = jsTrimSpace(part)
		if part == "" {
			continue
		}
		key, value, found := strings.Cut(part, "=")
		key = jsTrimSpace(key)
		if key == "" {
			continue
		}
		if !found || jsTrimSpace(value) == "" {
			opts[key] = true
		} else {
			opts[key] = jsTrimSpace(value)
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

// normalizeSSPlugin mirrors upstream normalizeSsPlugin: only obfs, v2ray, xray
// and gost plugins are renamed to lowercase; other names keep their original case.
func normalizeSSPlugin(plugin string, options map[string]any) (string, map[string]any) {
	rawPlugin := jsTrimSpace(plugin)
	switch plugin = strings.ToLower(rawPlugin); plugin {
	case "":
		return "", nil
	case "obfs", "obfs-local", "simple-obfs":
		result := map[string]any{}
		if mode := cmp.Or(pickString(options["mode"]), pickString(options["obfs"])); mode != "" {
			result["mode"] = mode
		}
		if host := cmp.Or(pickString(options["host"]), pickString(options["obfs-host"]), pickString(options["obfs_host"])); host != "" {
			result["host"] = host
		}
		if len(result) == 0 {
			return "obfs", nil
		}
		return "obfs", result
	case "v2ray-plugin", "xray-plugin", "gost-plugin":
		if options == nil {
			return plugin, nil
		}
		return plugin, normalizeSSPluginOptions(options)
	}
	return rawPlugin, options
}

func normalizeSSPluginOptions(options map[string]any) map[string]any {
	result := maps.Clone(options)
	for _, key := range []string{"mux", "tls"} {
		if value, ok := parseBooleanish(result[key]); ok {
			result[key] = value
		}
	}
	return result
}
