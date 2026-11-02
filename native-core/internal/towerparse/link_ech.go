// Ported from SubBoost packages/core/src/mihomo/ech.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"encoding/base64"
	"net/url"
	"regexp"
	"strings"
)

var (
	standardECHBase64Pattern = regexp.MustCompile(`^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$`)
	echDNSLabelPattern       = regexp.MustCompile(`^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$`)
	echResolverSchemePattern = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9+.-]*$`)
)

func isStandardECHBase64(value string) bool {
	value = jsTrimSpace(value)
	if value == "" || len(value)%4 != 0 || !standardECHBase64Pattern.MatchString(value) {
		return false
	}
	_, err := base64.StdEncoding.DecodeString(value)
	return err == nil
}

func isMihomoECHQueryServerName(value string) bool {
	value = jsTrimSpace(value)
	value = strings.TrimSuffix(value, ".")
	if value == "" || len(value) > 253 || !strings.Contains(value, ".") {
		return false
	}
	for _, char := range value {
		if (char >= 'A' && char <= 'Z') || (char >= 'a' && char <= 'z') {
			goto hasLetter
		}
	}
	return false

hasLetter:
	for _, label := range strings.Split(value, ".") {
		if len(label) > 63 || !echDNSLabelPattern.MatchString(label) {
			return false
		}
	}
	return true
}

func hasAbsoluteECHResolverURI(value string) bool {
	separator := strings.Index(value, "://")
	if separator <= 0 || !echResolverSchemePattern.MatchString(value[:separator]) {
		return false
	}
	if separator+3 >= len(value) {
		return false
	}
	for _, char := range value {
		if char <= ' ' || char == 127 {
			return false
		}
	}
	parsed, err := url.Parse(value)
	return err == nil && parsed.IsAbs() && parsed.Host != "" && parsed.Hostname() != ""
}

func buildMihomoECHOpts(value string) map[string]any {
	normalized := jsTrimSpace(value)
	result := map[string]any{"enable": true}
	if normalized == "" {
		return result
	}
	if isStandardECHBase64(normalized) {
		result["config"] = normalized
		return result
	}
	if isMihomoECHQueryServerName(normalized) {
		result["query-server-name"] = normalized
		return result
	}
	if separator := strings.IndexByte(normalized, '+'); separator > 0 {
		queryServerName := jsTrimSpace(normalized[:separator])
		resolverURI := jsTrimSpace(normalized[separator+1:])
		if isMihomoECHQueryServerName(queryServerName) && hasAbsoluteECHResolverURI(resolverURI) {
			result["query-server-name"] = queryServerName
		}
	}
	return result
}
