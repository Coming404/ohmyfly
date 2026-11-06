// 根据上游根语法识别 YAML，链接与 Base64 保持独立入口。
package towerparse

import (
	"bytes"
	"encoding/base64"
	"regexp"
)

var (
	schemeLine          = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9+.-]*://`)
	yamlRootLine        = regexp.MustCompile(`(?m)^[ \t]*(?:proxies|proxy-groups|proxy-providers)[ \t]*:`)
	platformHeader      = regexp.MustCompile(`^\[.+\]$`)
	platformProxyHeader = regexp.MustCompile(`(?i)^\[[ \t]*(?:proxy|server_local|server_remote|wireguard\s+)`)
)

// DetectFormat 在有界前缀中分流，完整字段验证由各解析器完成。
func DetectFormat(prefix []byte) Format {
	text := bytes.TrimSpace(bytes.TrimPrefix(prefix, []byte{0xef, 0xbb, 0xbf}))
	if len(text) == 0 {
		return FormatUnknown
	}
	if isClashYAMLContent(text) {
		return FormatYAML
	}
	for _, line := range bytes.FieldsFunc(text, func(r rune) bool { return r == '\r' || r == '\n' }) {
		if schemeLine.Match(bytes.TrimSpace(line)) {
			return FormatLink
		}
	}
	if isBase64Prefix(text) {
		return FormatBase64
	}
	return FormatUnknown
}

func isClashYAMLContent(text []byte) bool {
	if yamlRootLine.Match(text) {
		return true
	}
	for _, line := range bytes.FieldsFunc(text, func(r rune) bool { return r == '\r' || r == '\n' }) {
		line = bytes.TrimSpace(line)
		if len(line) == 0 || bytes.ContainsRune([]byte("#;%"), rune(line[0])) {
			continue
		}
		if bytes.HasPrefix(line, []byte("---")) && (len(line) == 3 || line[3] == ' ' || line[3] == '\t') {
			line = bytes.TrimSpace(line[3:])
			if len(line) == 0 || line[0] == '#' {
				continue
			}
		}
		if schemeLine.Match(line) || platformHeader.Match(line) && isPlatformDocument(text, line) {
			return false
		}
		if bytes.ContainsRune([]byte("{[!&"), rune(line[0])) {
			return true
		}
		if (line[0] == '-' || line[0] == '?') && (len(line) == 1 || line[1] == ' ' || line[1] == '\t') {
			return true
		}
		for i, char := range line {
			if char == ':' && (i+1 == len(line) || line[i+1] == ' ' || line[i+1] == '\t') {
				return true
			}
		}
		return false
	}
	return false
}

// 平台配置节名与 YAML 流式数组重叠，合法数组优先按 YAML 处理。
func isPlatformDocument(text, header []byte) bool {
	root, err := decodeSingleYAML(string(text))
	if _, ok := root.([]any); err == nil && ok {
		return false
	}
	for _, line := range bytes.FieldsFunc(text, func(r rune) bool { return r == '\r' || r == '\n' }) {
		line = bytes.TrimSpace(line)
		if platformHeader.Match(line) && platformProxyHeader.Match(line) {
			return true
		}
	}
	if err != nil {
		value, headerErr := decodeSingleYAML(string(header))
		if values, ok := value.([]any); headerErr == nil && ok && len(values) == 1 {
			_, isString := values[0].(string)
			return isString
		}
	}
	return false
}

func isBase64Prefix(value []byte) bool {
	if len(value) == 0 {
		return false
	}
	for _, char := range value {
		if (char >= 'A' && char <= 'Z') || (char >= 'a' && char <= 'z') ||
			(char >= '0' && char <= '9') || char == '+' || char == '/' ||
			char == '-' || char == '_' || char == '=' || char == '\r' || char == '\n' ||
			char == '\t' || char == ' ' {
			continue
		}
		return false
	}
	compact := bytes.Map(func(r rune) rune {
		switch r {
		case ' ', '\t', '\r', '\n', '=':
			return -1
		case '-':
			return '+'
		case '_':
			return '/'
		default:
			return r
		}
	}, value)
	if len(compact) < 4 {
		return false
	}
	// The prefix may end inside a quartet; check whole quartets only.
	compact = compact[:len(compact)-len(compact)%4]
	_, err := base64.RawStdEncoding.DecodeString(string(compact))
	return err == nil
}
