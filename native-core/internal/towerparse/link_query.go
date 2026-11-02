// Query helpers shared by the QUIC-family parsers.
// Ported from SubBoost packages/core/src/parser/protocols/hysteria2.ts and tuic.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"net/url"
	"regexp"
	"strconv"
	"strings"
)

// linkQueryAlias maps an output field to the query keys that may carry it, in priority order.
type linkQueryAlias struct {
	target string
	keys   []string
}

var linkIntegerPrefixPattern = regexp.MustCompile(`^[+-]?\d+`)

// linkOptionalBool returns the first value among keys that parses as a boolean.
func linkOptionalBool(query url.Values, keys ...string) (bool, bool) {
	for _, key := range keys {
		if value, ok := linkBool(query.Get(key)); ok {
			return value, true
		}
	}
	return false, false
}

// linkStrictTrue reports whether any key is exactly "1" or "true".
func linkStrictTrue(query url.Values, keys ...string) bool {
	for _, key := range keys {
		if value := query.Get(key); value == "1" || value == "true" {
			return true
		}
	}
	return false
}

// linkIntegerPrefix parses the leading integer of the first key that has one, like JavaScript parseInt.
func linkIntegerPrefix(query url.Values, keys ...string) (int, bool) {
	for _, key := range keys {
		prefix := linkIntegerPrefixPattern.FindString(strings.TrimSpace(query.Get(key)))
		if prefix == "" {
			continue
		}
		if value, err := strconv.Atoi(prefix); err == nil {
			return value, true
		}
	}
	return 0, false
}
