package towerparse

import (
	"bytes"
	"encoding/base64"
	"regexp"
)

var schemeLine = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9+.-]*://`)

// DetectFormat classifies a bounded prefix without consuming a reader.
func DetectFormat(prefix []byte) Format {
	text := bytes.TrimSpace(bytes.TrimPrefix(prefix, []byte{0xef, 0xbb, 0xbf}))
	if len(text) == 0 {
		return FormatUnknown
	}
	for _, line := range bytes.Split(text, []byte{'\n'}) {
		line = bytes.TrimSpace(bytes.TrimSuffix(line, []byte{'\r'}))
		if len(line) == 0 || line[0] == '#' {
			continue
		}
		if bytes.HasPrefix(line, []byte("---")) || hasYAMLRootKey(line) {
			return FormatYAML
		}
		if schemeLine.Match(line) {
			return FormatLink
		}
		if bytes.HasPrefix(line, []byte("- name:")) || bytes.HasPrefix(line, []byte("- type:")) {
			return FormatYAML
		}
	}
	if isBase64Prefix(text) {
		return FormatBase64
	}
	return FormatUnknown
}

// yamlRootKeys are the target root keys, bare or quoted, that mark a YAML document.
var yamlRootKeys = [][]byte{
	[]byte("proxies:"), []byte("proxy-providers:"),
	[]byte(`"proxies":`), []byte(`"proxy-providers":`),
	[]byte("'proxies':"), []byte("'proxy-providers':"),
}

func hasYAMLRootKey(line []byte) bool {
	for _, key := range yamlRootKeys {
		if bytes.HasPrefix(line, key) {
			return true
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
