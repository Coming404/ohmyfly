// Ported from SubBoost packages/core/src/parser/protocols/ssr.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"fmt"
	"net/url"
	"strconv"
	"strings"
)

func parseSSRLink(raw string) (map[string]any, error) {
	raw = normalizeLinkScheme(raw, "ssr")
	content := strings.TrimPrefix(raw, "ssr://")
	if jsTrimSpace(content) == "" {
		return nil, fmt.Errorf("empty ssr link")
	}
	decoded, err := decodeLinkText(content)
	if err != nil {
		return nil, err
	}
	main := decoded
	queryText := ""
	if index := strings.Index(decoded, "/?"); index >= 0 {
		main, queryText = decoded[:index], decoded[index+2:]
	} else if index := strings.IndexByte(decoded, '?'); index >= 0 {
		main, queryText = decoded[:index], decoded[index+1:]
	}
	parts := strings.Split(main, ":")
	if len(parts) < 6 {
		return nil, fmt.Errorf("invalid ssr format")
	}
	passwordEncoded := parts[len(parts)-1]
	obfs := jsTrimSpace(parts[len(parts)-2])
	cipher := jsTrimSpace(parts[len(parts)-3])
	protocol := jsTrimSpace(parts[len(parts)-4])
	portText := jsTrimSpace(parts[len(parts)-5])
	server := jsTrimSpace(strings.Join(parts[:len(parts)-5], ":"))
	if strings.HasPrefix(server, "[") && strings.HasSuffix(server, "]") {
		server = server[1 : len(server)-1]
	}
	port, err := strconv.Atoi(portText)
	if err != nil || port < 1 || port > 65535 {
		return nil, fmt.Errorf("invalid port")
	}
	if server == "" {
		return nil, fmt.Errorf("missing server")
	}
	password, err := decodeSSRValue(passwordEncoded)
	if err != nil {
		return nil, err
	}
	params := parseSSRQuery(queryText)
	remarks, err := decodeSSRValue(firstSSRParam(params, "remarks", "remark"))
	if err != nil {
		return nil, err
	}
	name := remarks
	if name == "" {
		name = linkDefaultName(server, port)
	}
	node := map[string]any{
		"name":     name,
		"type":     "ssr",
		"server":   server,
		"port":     port,
		"cipher":   cipher,
		"password": password,
		"protocol": protocol,
		"obfs":     obfs,
		"udp":      true,
	}
	if node["cipher"] == "" {
		node["cipher"] = "aes-256-cfb"
	}
	if node["protocol"] == "" {
		node["protocol"] = "origin"
	}
	if node["obfs"] == "" {
		node["obfs"] = "plain"
	}
	if value, err := decodeSSRValue(firstSSRParam(params, "protoparam", "protocol-param")); err != nil {
		return nil, err
	} else if value != "" {
		node["protocol-param"] = value
	}
	if value, err := decodeSSRValue(firstSSRParam(params, "obfsparam", "obfs-param")); err != nil {
		return nil, err
	} else if value != "" {
		node["obfs-param"] = value
	}
	return node, nil
}

func decodeSSRValue(value string) (string, error) {
	if jsTrimSpace(value) == "" {
		return "", nil
	}
	if decoded, err := decodeLinkText(value); err == nil {
		return decoded, nil
	}
	decoded, err := url.QueryUnescape(value)
	if err != nil {
		return "", err
	}
	return decoded, nil
}

func parseSSRQuery(value string) map[string]string {
	result := map[string]string{}
	for _, part := range strings.Split(strings.TrimPrefix(value, "?"), "&") {
		if part == "" {
			continue
		}
		key, item, found := strings.Cut(part, "=")
		if !found || jsTrimSpace(key) == "" {
			continue
		}
		result[jsTrimSpace(key)] = item
	}
	return result
}

func firstSSRParam(params map[string]string, keys ...string) string {
	for _, key := range keys {
		if value := params[key]; value != "" {
			return value
		}
	}
	return ""
}
