// 移植自 SubBoost packages/core/src/parser/protocols/vmess-utils.ts @4015347（AGPL-3.0-only）。
package towerparse

import (
	"encoding/json"
	"fmt"
	"net/url"
	"regexp"
	"strings"
)

const dingtalkUserAgent = "DingTalk/50636215 CFNetwork/3826.600.41 Darwin/24.6.0"

// jsSpaceClass 是 JavaScript 正则 \s 匹配的字符集；Go 的 \s 只含 ASCII 空白。
const jsSpaceClass = `\t\n\v\f\r \x{a0}\x{1680}\x{2000}-\x{200a}\x{2028}\x{2029}\x{202f}\x{205f}\x{3000}\x{feff}`

var (
	vmessHTTPMethodPattern   = regexp.MustCompile(`^[A-Z][A-Z0-9-]*$`)
	vmessHostHeaderPattern   = regexp.MustCompile(`(?i)Host:[` + jsSpaceClass + `]*([^,;` + jsSpaceClass + `]+)`)
	vmessStandardPattern     = regexp.MustCompile(`(?i)^[a-z]+(?:\+[a-z]+)?:[^@` + jsSpaceClass + `]+-\d+@[^` + jsSpaceClass + `]+:\d+`)
	vmessShadowrocketPattern = regexp.MustCompile(`(^[^:]+?):([^:]+?)@(.+):(\d+)$`)
)

func hasDingTalkHost(hosts []string) bool {
	for _, host := range hosts {
		host = strings.ToLower(jsTrimSpace(host))
		if host == "dingtalk.com" || strings.HasSuffix(host, ".dingtalk.com") {
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
		case float64:
			values = []string{jsNumberString(value)}
		case bool, int, int64, json.Number:
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
	return err == nil && vmessShadowrocketPattern.MatchString(decoded)
}

func isAlphaString(value string) bool {
	for _, char := range value {
		if (char < 'a' || char > 'z') && (char < 'A' || char > 'Z') {
			return false
		}
	}
	return value != ""
}

func isDigitString(value string) bool {
	for _, char := range value {
		if char < '0' || char > '9' {
			return false
		}
	}
	return value != ""
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

// parseObfsHeaderHost 从 "Host: a.example.com, ..." 中取出主机名；没有 Host 字段时返回解码后的整串。
func parseObfsHeaderHost(raw string) string {
	if raw == "" {
		return ""
	}
	decoded := linkDecodeForm(stripOuterQuotes(raw))
	if match := vmessHostHeaderPattern.FindStringSubmatch(decoded); match != nil {
		if host := jsTrimSpace(match[1]); host != "" {
			return host
		}
	}
	return decoded
}
