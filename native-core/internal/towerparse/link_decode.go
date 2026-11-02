// Ported from SubBoost packages/core/src/parser/protocols/url-decode.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/url"
	"strconv"
	"strings"
)

func decodeLinkBase64(value string) ([]byte, error) {
	value, err := url.PathUnescape(strings.TrimSpace(value))
	if err != nil {
		return nil, err
	}
	value = strings.Map(func(char rune) rune {
		switch char {
		case ' ', '\t', '\r', '\n':
			return -1
		default:
			return char
		}
	}, value)
	encodings := []*base64.Encoding{
		base64.RawURLEncoding,
		base64.URLEncoding,
		base64.RawStdEncoding,
		base64.StdEncoding,
	}
	for _, encoding := range encodings {
		decoded, err := encoding.DecodeString(value)
		if err == nil {
			return decoded, nil
		}
	}
	return nil, fmt.Errorf("invalid base64 payload")
}

func decodeLinkText(value string) (string, error) {
	decoded, err := decodeLinkBase64(value)
	if err != nil {
		return "", err
	}
	return string(decoded), nil
}

func linkString(query url.Values, keys ...string) string {
	for _, key := range keys {
		if value := strings.TrimSpace(query.Get(key)); value != "" {
			return value
		}
	}
	return ""
}

func linkBool(value string) (bool, bool) {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "1", "true", "yes", "on":
		return true, true
	case "0", "false", "no", "off":
		return false, true
	default:
		return false, false
	}
}

func linkBoolValue(query url.Values, keys ...string) (bool, bool) {
	for _, key := range keys {
		if value, exists := query[key]; exists {
			if len(value) == 0 || strings.TrimSpace(value[0]) == "" {
				return true, true
			}
			return linkBool(value[0])
		}
	}
	return false, false
}

func linkList(value string) []string {
	var result []string
	for _, item := range strings.Split(value, ",") {
		if item = strings.TrimSpace(item); item != "" {
			result = append(result, item)
		}
	}
	return result
}

func linkPort(value string, fallback int) (int, error) {
	if strings.TrimSpace(value) == "" {
		return fallback, nil
	}
	port, err := strconv.Atoi(strings.TrimSpace(value))
	if err != nil || port < 1 || port > 65535 {
		return 0, fmt.Errorf("invalid port")
	}
	return port, nil
}

func linkName(value, fallback string) string {
	if value == "" {
		return fallback
	}
	decoded, err := url.QueryUnescape(value)
	if err == nil && decoded != "" {
		return decoded
	}
	return value
}

func linkURL(raw string) (*url.URL, url.Values, error) {
	parsed, err := url.Parse(raw)
	if err != nil {
		return nil, nil, err
	}
	query, err := url.ParseQuery(parsed.RawQuery)
	if err != nil {
		return nil, nil, err
	}
	return parsed, query, nil
}

func linkAuthority(parsed *url.URL, defaultPort int) (string, int, error) {
	server := parsed.Hostname()
	if server == "" {
		return "", 0, fmt.Errorf("missing server")
	}
	port, err := linkPort(parsed.Port(), defaultPort)
	if err != nil {
		return "", 0, err
	}
	return server, port, nil
}

func addLinkBool(node map[string]any, query url.Values, target string, keys ...string) {
	if value, ok := linkBoolValue(query, keys...); ok {
		node[target] = value
	}
}

func addLinkAlpn(node map[string]any, value string) {
	if values := linkList(value); len(values) > 0 {
		node["alpn"] = values
	}
}

func parseLinkJSON(value string) (map[string]any, error) {
	var object map[string]any
	if err := json.Unmarshal([]byte(value), &object); err != nil {
		return nil, err
	}
	return object, nil
}

func linkHeaderMap(value string) map[string]any {
	value = strings.TrimSpace(value)
	if value == "" {
		return nil
	}
	var decoded map[string]any
	if json.Unmarshal([]byte(value), &decoded) == nil {
		for key, item := range decoded {
			if _, ok := item.(string); !ok {
				delete(decoded, key)
			}
		}
		if len(decoded) > 0 {
			return decoded
		}
		return nil
	}
	if strings.HasPrefix(value, "{") || strings.HasPrefix(value, "[") {
		return nil
	}
	result := map[string]any{}
	for _, part := range strings.Split(value, "|") {
		key, item, ok := strings.Cut(part, ":")
		if !ok || strings.TrimSpace(key) == "" || strings.TrimSpace(item) == "" {
			continue
		}
		result[strings.TrimSpace(key)] = strings.TrimSpace(item)
	}
	if len(result) == 0 {
		return nil
	}
	return result
}

func splitLinkEarlyData(path, fallback string) (string, int) {
	if path == "" {
		path = fallback
	}
	question := strings.IndexByte(path, '?')
	if question < 0 {
		return path, 0
	}
	base := path[:question]
	if base == "" {
		base = fallback
	}
	for _, part := range strings.Split(path[question+1:], "&") {
		key, value, ok := strings.Cut(part, "=")
		if ok && key == "ed" {
			earlyData, err := strconv.Atoi(value)
			if err == nil && earlyData > 0 {
				return base, earlyData
			}
		}
	}
	return path, 0
}

func linkECHOpts(value string) map[string]any {
	return buildMihomoECHOpts(value)
}
