// Ported from SubBoost packages/core/src/parser/protocols/vmess-utils.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"encoding/json"
	"fmt"
	"net/url"
	"regexp"
	"strconv"
	"strings"
)

func linkHeaderValues(value any) []string {
	switch value := value.(type) {
	case []any:
		result := make([]string, 0, len(value))
		for _, item := range value {
			result = append(result, fmt.Sprint(item))
		}
		return result
	case []string:
		return value
	default:
		return []string{fmt.Sprint(value)}
	}
}

func stringField(node map[string]any, keys ...string) string {
	for _, key := range keys {
		value, ok := node[key]
		if !ok {
			continue
		}
		text := jsTrimSpace(fmt.Sprint(value))
		if text != "" {
			return text
		}
	}
	return ""
}

func intField(node map[string]any, fallback int, keys ...string) int {
	for _, key := range keys {
		value, ok := node[key]
		if !ok {
			continue
		}
		switch value := value.(type) {
		case float64:
			return int(value)
		case json.Number:
			parsed, err := strconv.Atoi(string(value))
			if err == nil {
				return parsed
			}
		case string:
			parsed, err := strconv.Atoi(jsTrimSpace(value))
			if err == nil {
				return parsed
			}
		default:
			parsed, err := strconv.Atoi(fmt.Sprint(value))
			if err == nil {
				return parsed
			}
		}
	}
	return fallback
}
func boolField(node map[string]any, keys ...string) (bool, bool) {
	for _, key := range keys {
		value, ok := node[key]
		if !ok {
			continue
		}
		if parsed, valid := parseBooleanish(value); valid {
			return parsed, true
		}
	}
	return false, false
}

const dingtalkUserAgent = "DingTalk/50636215 CFNetwork/3826.600.41 Darwin/24.6.0"

var (
	vmessHTTPMethodPattern = regexp.MustCompile(`^[A-Z][A-Z0-9-]*$`)
	vmessHostHeaderPattern = regexp.MustCompile(`(?i)Host:\s*([^,;\s]+)`)
	vmessStandardPattern   = regexp.MustCompile(`^[a-z]+(?:\+[a-z]+)?:[^@\s]+-\d+@[^\s]+:\d+`)
)

func hasDingTalkHost(hosts []string) bool {
	for _, host := range hosts {
		host = jsTrimSpace(host)
		if len(host) >= len("dingtalk.com") && strings.EqualFold(host[len(host)-len("dingtalk.com"):], "dingtalk.com") &&
			(len(host) == len("dingtalk.com") || host[len(host)-len("dingtalk.com")-1] == '.') {
			return true
		}
	}
	return false
}

func normalizeHTTPMethod(input any) string {
	value, ok := input.(string)
	if !ok {
		return "GET"
	}
	value = strings.ToUpper(jsTrimSpace(value))
	if value == "" || !vmessHTTPMethodPattern.MatchString(value) {
		return "GET"
	}
	return value
}

func normalizeHeaderKey(key string) string {
	key = jsTrimSpace(key)
	switch strings.ToLower(key) {
	case "host":
		return "Host"
	case "user-agent":
		return "User-Agent"
	case "connection":
		return "Connection"
	default:
		return key
	}
}

func parseHeaderRecord(input any) map[string]any {
	record, ok := input.(map[string]any)
	if !ok || record == nil {
		return nil
	}
	result := map[string]any{}
	for rawKey, rawValue := range record {
		key := normalizeHeaderKey(rawKey)
		if key == "" {
			continue
		}
		values := []string{}
		switch value := rawValue.(type) {
		case []any:
			for _, item := range value {
				if text, ok := item.(string); ok && jsTrimSpace(text) != "" {
					values = append(values, jsTrimSpace(text))
				}
			}
		case []string:
			for _, item := range value {
				if jsTrimSpace(item) != "" {
					values = append(values, jsTrimSpace(item))
				}
			}
		case string:
			if jsTrimSpace(value) != "" {
				values = []string{jsTrimSpace(value)}
			}
		case bool, float64, int, int64, json.Number:
			values = []string{fmt.Sprint(value)}
		}
		if len(values) > 0 {
			result[key] = values
		}
	}
	if len(result) == 0 {
		return nil
	}
	return result
}

func splitList(value string) []string {
	return linkList(value)
}

func pickString(value any) string {
	if text, ok := value.(string); ok {
		return jsTrimSpace(text)
	}
	return ""
}

func parseBooleanish(value any) (bool, bool) {
	switch value := value.(type) {
	case bool:
		return value, true
	case int:
		if value == 0 || value == 1 {
			return value == 1, true
		}
	case int64:
		if value == 0 || value == 1 {
			return value == 1, true
		}
	case float64:
		if value == 0 || value == 1 {
			return value == 1, true
		}
	case json.Number:
		if value == "0" || value == "1" {
			return value == "1", true
		}
	case string:
		return linkBool(value)
	}
	return false, false
}

func looksLikeURIStyleVMess(content string) bool {
	return strings.Contains(content, "@") || strings.Contains(content, "?")
}

func looksLikeStandardVMessStyle(content string) bool {
	return vmessStandardPattern.MatchString(content)
}

func looksLikeShadowrocketStyleVMess(content string) bool {
	queryIndex := strings.IndexByte(content, '?')
	if queryIndex <= 0 {
		return false
	}
	base := strings.TrimSuffix(content[:queryIndex], "/")
	if base == "" || strings.Contains(base, "@") {
		return false
	}
	decoded, err := decodeLinkText(base)
	if err != nil {
		return false
	}
	return regexp.MustCompile(`^[^:]+?:[^:]+?@.+:\d+$`).MatchString(decoded)
}

func stripOuterQuotes(value string) string {
	value = jsTrimSpace(value)
	if strings.HasPrefix(value, "\"") || strings.HasPrefix(value, "'") {
		value = value[1:]
	}
	if strings.HasSuffix(value, "\"") || strings.HasSuffix(value, "'") {
		value = value[:len(value)-1]
	}
	return value
}

func pickQueryParam(query url.Values, keys ...string) string {
	for _, key := range keys {
		if value := query.Get(key); value != "" {
			return value
		}
	}
	return ""
}

func parseObfsHeaderHost(raw string) string {
	if raw == "" {
		return ""
	}
	decoded, err := url.QueryUnescape(stripOuterQuotes(raw))
	if err != nil {
		decoded = stripOuterQuotes(raw)
	}
	if match := vmessHostHeaderPattern.FindStringSubmatch(decoded); len(match) > 1 {
		return jsTrimSpace(match[1])
	}
	if decoded == "" {
		return ""
	}
	return decoded
}

func normalizeSSPlugin(plugin string, options map[string]any) (string, map[string]any) {
	plugin = jsTrimSpace(plugin)
	if strings.EqualFold(plugin, "obfs-local") || strings.EqualFold(plugin, "simple-obfs") || strings.EqualFold(plugin, "obfs") {
		result := map[string]any{}
		if value := stringField(options, "obfs"); value != "" {
			result["mode"] = value
		}
		if value := stringField(options, "obfs-host"); value != "" {
			result["host"] = value
		} else if value := stringField(options, "host"); value != "" {
			result["host"] = value
		}
		if len(result) == 0 {
			return "obfs", nil
		}
		return "obfs", result
	}
	if plugin == "" {
		return "", nil
	}
	if len(options) == 0 {
		return strings.ToLower(plugin), nil
	}
	return strings.ToLower(plugin), normalizeSSPluginOptions(options)
}
