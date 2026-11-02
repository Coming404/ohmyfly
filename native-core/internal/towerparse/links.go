// Ported from SubBoost packages/core/src/parser/link-parsers.ts @4015347 (AGPL-3.0-only).
package towerparse

import "strings"

func parseLink(text string) (map[string]any, bool, bool, error) {
	match := linkScheme.FindStringSubmatch(text)
	if match == nil {
		return nil, false, false, nil
	}
	scheme := strings.ToLower(match[1])
	normalized := normalizeLinkScheme(text, scheme)
	var (
		node       map[string]any
		fromBase64 bool
		err        error
	)
	switch scheme {
	case "vless":
		node, err = parseVLESSLink(normalized)
	case "vmess", "vmess1":
		node, fromBase64, err = parseVMessLink(normalized)
	case "ss":
		node, err = parseSSLink(normalized)
	case "trojan":
		node, err = parseTrojanLink(normalized)
	case "ssr":
		node, err = parseSSRLink(normalized)
	case "hysteria", "hy":
		node, err = parseHysteriaLink(normalized)
	case "hysteria2", "hy2":
		node, err = parseHysteria2Link(normalized)
	case "tuic":
		node, err = parseTUICLink(normalized)
	default:
		return nil, false, false, nil
	}
	return node, fromBase64, true, err
}

func normalizeLinkScheme(raw, scheme string) string {
	prefix := scheme + "://"
	if len(raw) < len(prefix) {
		return raw
	}
	return prefix + raw[len(prefix):]
}
