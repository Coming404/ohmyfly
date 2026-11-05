// Ported from SubBoost packages/core/src/parser/protocols/vless-xhttp.ts @4015347 (AGPL-3.0-only).
// XHTTP extra 字段映射、整数与范围规范化、上游默认值。
package towerparse

import (
	"encoding/json"
	"fmt"
	"math"
	"strconv"
	"strings"
)

func mapXHTTPExtra(source map[string]any, defaults bool) (map[string]any, error) {
	result := map[string]any{}
	for sourceKey, target := range xhttpExtraFields {
		if value, ok := source[sourceKey]; ok {
			result[target] = value
		}
	}
	if raw, ok := source["headers"]; ok {
		headers, err := mapXHTTPHeaders(raw, false)
		if err != nil {
			return nil, err
		}
		result["headers"] = headers
	}
	for _, field := range []string{"x-padding-bytes", "session-length", "uplink-chunk-size", "sc-max-each-post-bytes", "sc-min-posts-interval-ms"} {
		if value, ok := result[field]; ok {
			normalized, err := normalizeXHTTPRange(value, field, true)
			if err != nil {
				return nil, err
			}
			if field == "sc-max-each-post-bytes" && normalized != "" && !strings.Contains(normalized, "-") {
				result[field], _ = strconv.Atoi(normalized)
			} else {
				result[field] = normalized
			}
		}
	}
	if value, ok := result["x-padding-bytes"]; ok && (value == "0" || value == "") {
		result["x-padding-bytes"] = "100-1000"
	}
	if value, ok := result["sc-max-each-post-bytes"]; ok && (value == "0" || value == 0) {
		result["sc-max-each-post-bytes"] = 1000000
	}
	if value, ok := result["sc-min-posts-interval-ms"]; ok && (value == "0" || value == 0) {
		result["sc-min-posts-interval-ms"] = "30"
	}
	if value, ok := result["x-padding-bytes"].(string); ok {
		for _, part := range strings.Split(value, "-") {
			if part == "0" {
				return nil, fmt.Errorf("XHTTP xPaddingBytes cannot contain zero endpoint")
			}
		}
	}
	if raw, ok := source["xmux"]; ok {
		reuse, err := mapXHTTPReuse(raw)
		if err != nil {
			return nil, err
		}
		result["reuse-settings"] = reuse
	} else {
		result["reuse-settings"] = map[string]any{}
	}
	if result["session-table"] == "uuid" {
		return nil, fmt.Errorf("XHTTP sessionIDTable uuid is reserved")
	}
	if defaults {
		return applyXHTTPSourceDefaults(result)
	}
	return normalizeXHTTPFields(result)
}

func mapXHTTPHeaders(value any, allowHost bool) (map[string]any, error) {
	source, ok := value.(map[string]any)
	if !ok || source == nil {
		return nil, fmt.Errorf("XHTTP headers need string map")
	}
	result := map[string]any{}
	for key, item := range source {
		text, ok := item.(string)
		if !ok {
			return nil, fmt.Errorf("XHTTP headers need string map")
		}
		if !allowHost && strings.EqualFold(key, "host") {
			return nil, fmt.Errorf("XHTTP Host must use host field")
		}
		result[key] = text
	}
	return result, nil
}

func mapXHTTPReuse(value any) (map[string]any, error) {
	if value == nil {
		value = map[string]any{}
	}
	source, ok := value.(map[string]any)
	if !ok || source == nil {
		return nil, fmt.Errorf("XHTTP xmux needs object")
	}
	result := map[string]any{}
	for sourceKey, target := range xhttpReuseFields {
		if item, exists := source[sourceKey]; exists {
			result[target] = item
		}
	}
	for _, field := range []string{"max-concurrency", "max-connections", "c-max-reuse-times", "h-max-request-times", "h-max-reusable-secs"} {
		if item, exists := result[field]; exists {
			normalized, err := normalizeXHTTPRange(item, field, true)
			if err != nil {
				return nil, err
			}
			result[field] = normalized
		}
	}
	return normalizeXHTTPReuseCanonical(result)
}

const maxXHTTPSafeInteger int64 = 1<<53 - 1

func xhttpFloatIntegerText(value float64) (string, bool) {
	limit := float64(maxXHTTPSafeInteger)
	if math.IsInf(value, 0) || math.Trunc(value) != value || value > limit || value < -limit {
		return "", false
	}
	if value == 0 {
		return "0", true
	}
	return strconv.FormatFloat(value, 'f', -1, 64), true
}

func xhttpSignedIntegerText(value int64) (string, bool) {
	if value > maxXHTTPSafeInteger || value < -maxXHTTPSafeInteger {
		return "", false
	}
	return strconv.FormatInt(value, 10), true
}

func xhttpUnsignedIntegerText(value uint64) (string, bool) {
	if value > uint64(maxXHTTPSafeInteger) {
		return "", false
	}
	return strconv.FormatUint(value, 10), true
}

func xhttpIntegerText(value any) (string, bool) {
	switch value := value.(type) {
	case float64:
		return xhttpFloatIntegerText(value)
	case int:
		return xhttpSignedIntegerText(int64(value))
	case int8:
		return xhttpSignedIntegerText(int64(value))
	case int16:
		return xhttpSignedIntegerText(int64(value))
	case int32:
		return xhttpSignedIntegerText(int64(value))
	case int64:
		return xhttpSignedIntegerText(value)
	case uint:
		return xhttpUnsignedIntegerText(uint64(value))
	case uint8:
		return xhttpUnsignedIntegerText(uint64(value))
	case uint16:
		return xhttpUnsignedIntegerText(uint64(value))
	case uint32:
		return xhttpUnsignedIntegerText(uint64(value))
	case uint64:
		return xhttpUnsignedIntegerText(value)
	case uintptr:
		return xhttpUnsignedIntegerText(uint64(value))
	case json.Number:
		parsed, err := strconv.ParseFloat(string(value), 64)
		if err != nil {
			return "", false
		}
		return xhttpFloatIntegerText(parsed)
	case string:
		return value, true
	default:
		return "", false
	}
}

func xhttpIntegerValue(value any) (int, bool) {
	if text, isText := value.(string); isText {
		digits := strings.TrimPrefix(text, "-")
		if digits == "" || strings.Trim(digits, "0123456789") != "" {
			return 0, false
		}
	}
	text, ok := xhttpIntegerText(value)
	if !ok {
		return 0, false
	}
	parsed, err := strconv.Atoi(text)
	if err != nil || int64(parsed) > maxXHTTPSafeInteger || int64(parsed) < -maxXHTTPSafeInteger {
		return 0, false
	}
	return parsed, true
}

// normalizeXHTTPRange canonicalizes an XHTTP range. source=true mirrors upstream
// sourceRange (empty means "0", reversed bounds are swapped); source=false mirrors
// mihomo normalizeXhttpRange (empty stays empty, reversed bounds are rejected).
func normalizeXHTTPRange(value any, field string, source bool) (string, error) {
	text, ok := xhttpIntegerText(value)
	if !ok {
		return "", fmt.Errorf("XHTTP %s needs non-negative integer or range", field)
	}
	text = jsTrimSpace(text)
	if text == "" {
		if source {
			return "0", nil
		}
		return "", nil
	}
	parts := strings.Split(text, "-")
	if len(parts) > 2 {
		return "", fmt.Errorf("XHTTP %s needs non-negative integer or range", field)
	}
	values := make([]int, len(parts))
	for index, part := range parts {
		part = strings.TrimPrefix(jsTrimSpace(part), "+")
		parsed, err := strconv.ParseUint(part, 10, 31)
		if err != nil {
			return "", fmt.Errorf("XHTTP %s range invalid", field)
		}
		values[index] = int(parsed)
	}
	if len(values) == 2 && values[0] > values[1] {
		if !source {
			return "", fmt.Errorf("XHTTP %s range invalid", field)
		}
		values[0], values[1] = values[1], values[0]
	}
	if len(values) == 1 || values[0] == values[1] {
		return strconv.Itoa(values[0]), nil
	}
	return strconv.Itoa(values[0]) + "-" + strconv.Itoa(values[1]), nil
}

func normalizeXHTTPFields(options map[string]any) (map[string]any, error) {
	result := map[string]any{}
	for key, value := range options {
		result[key] = value
	}
	if value, exists := result["mode"]; exists {
		if mode := fmt.Sprint(value); mode != "" && mode != "auto" && mode != "packet-up" && mode != "stream-up" && mode != "stream-one" {
			return nil, fmt.Errorf("XHTTP mode invalid")
		}
	}
	for _, field := range []string{"x-padding-bytes", "session-length", "uplink-chunk-size", "sc-max-each-post-bytes", "sc-min-posts-interval-ms"} {
		if value, exists := result[field]; exists {
			normalized, err := normalizeXHTTPRange(value, field, false)
			if err != nil {
				return nil, err
			}
			if normalized != "" && strings.HasPrefix(field, "sc-") && xhttpRangeMax(normalized) == 0 {
				return nil, fmt.Errorf("XHTTP %s must be positive", field)
			}
			if normalized != "" && field == "sc-max-each-post-bytes" && !strings.Contains(normalized, "-") {
				result[field], _ = strconv.Atoi(normalized)
			} else {
				result[field] = normalized
			}
		}
	}
	for _, field := range []string{"x-padding-placement", "x-padding-method", "session-placement", "seq-placement", "uplink-data-placement"} {
		if value, exists := result[field]; exists {
			text, ok := value.(string)
			if !ok {
				return nil, fmt.Errorf("XHTTP %s invalid", field)
			}
			allowed := map[string]bool{}
			switch field {
			case "x-padding-placement":
				allowed = map[string]bool{"queryInHeader": true, "cookie": true, "header": true, "query": true}
			case "x-padding-method":
				allowed = map[string]bool{"repeat-x": true, "tokenish": true}
			case "session-placement", "seq-placement":
				allowed = map[string]bool{"path": true, "query": true, "cookie": true, "header": true}
			case "uplink-data-placement":
				allowed = map[string]bool{"auto": true, "body": true, "cookie": true, "header": true}
			}
			if text != "" && !allowed[text] {
				return nil, fmt.Errorf("XHTTP %s invalid", field)
			}
		}
	}
	for _, field := range []string{"path", "host", "x-padding-key", "x-padding-header", "uplink-http-method", "session-key", "session-table", "seq-key", "uplink-data-key"} {
		if value, exists := result[field]; exists {
			if _, ok := value.(string); !ok {
				return nil, fmt.Errorf("XHTTP %s invalid", field)
			}
		}
	}
	for _, field := range []string{"no-grpc-header", "x-padding-obfs-mode"} {
		if value, exists := result[field]; exists {
			parsed, ok := parseBooleanish(value)
			if !ok {
				return nil, fmt.Errorf("XHTTP %s needs boolean", field)
			}
			result[field] = parsed
		}
	}
	if value, exists := result["headers"]; exists {
		if _, err := mapXHTTPHeaders(value, true); err != nil {
			return nil, err
		}
	}
	if value, exists := result["reuse-settings"]; exists {
		reuse, err := normalizeXHTTPReuseCanonical(value)
		if err != nil {
			return nil, err
		}
		result["reuse-settings"] = reuse
	}
	return result, nil
}

func normalizeXHTTPReuseCanonical(value any) (map[string]any, error) {
	source, ok := value.(map[string]any)
	if !ok || source == nil {
		return nil, fmt.Errorf("XHTTP reuse-settings needs object")
	}
	result := map[string]any{}
	for key, item := range source {
		result[key] = item
	}
	for _, field := range []string{"max-concurrency", "max-connections", "c-max-reuse-times", "h-max-request-times", "h-max-reusable-secs"} {
		if item, exists := result[field]; exists {
			normalized, err := normalizeXHTTPRange(item, field, false)
			if err != nil {
				return nil, err
			}
			result[field] = normalized
		}
	}
	if item, exists := result["h-keep-alive-period"]; exists {
		parsed, ok := xhttpIntegerValue(item)
		if !ok {
			return nil, fmt.Errorf("XHTTP h-keep-alive-period needs integer")
		}
		result["h-keep-alive-period"] = parsed
	}
	return result, nil
}

func applyXHTTPSourceDefaults(options map[string]any) (map[string]any, error) {
	result, err := normalizeXHTTPFields(options)
	if err != nil {
		return nil, err
	}
	reuse, _ := result["reuse-settings"].(map[string]any)
	if reuse == nil {
		reuse = map[string]any{}
	}
	maxPositive := false
	for _, field := range []string{"max-concurrency", "max-connections"} {
		if item, ok := reuse[field]; ok && xhttpRangeMax(item) > 0 {
			if maxPositive {
				return nil, fmt.Errorf("XHTTP xmux maxConcurrency and maxConnections conflict")
			}
			maxPositive = true
		}
	}
	allZero := true
	for _, field := range []string{"max-concurrency", "max-connections", "c-max-reuse-times", "h-max-request-times", "h-max-reusable-secs"} {
		if item, ok := reuse[field]; ok && xhttpRangeMax(item) != 0 {
			allZero = false
		}
	}
	hKeepAlivePeriod, hasKeepAlivePeriod := reuse["h-keep-alive-period"]
	if allZero && (!hasKeepAlivePeriod || hKeepAlivePeriod == 0) {
		reuse["max-connections"] = "3"
		reuse["h-max-request-times"] = "600-900"
		reuse["h-max-reusable-secs"] = "1800-3000"
	}
	result["reuse-settings"] = reuse
	sessionTable, _ := result["session-table"].(string)
	if sessionTable == "" {
		result["session-table"] = "uuid"
	}
	if result["session-table"] != "uuid" && xhttpRangeMin(result["session-length"]) == 0 {
		return nil, fmt.Errorf("XHTTP sessionIDTable needs positive sessionIDLength")
	}
	if value, ok := result["uplink-http-method"]; ok {
		result["uplink-http-method"] = strings.ToUpper(fmt.Sprint(value))
		if result["uplink-http-method"] == "" {
			result["uplink-http-method"] = "POST"
		}
	}
	placement, _ := result["uplink-data-placement"].(string)
	if placement == "" {
		placement = "auto"
	}
	dataKey, _ := result["uplink-data-key"].(string)
	if dataKey == "" && placement != "body" {
		if placement == "cookie" {
			result["uplink-data-key"] = "x_data"
		} else {
			result["uplink-data-key"] = "X-Data"
		}
	}
	chunkSize, _ := result["uplink-chunk-size"].(string)
	if placement == "header" && (chunkSize == "" || chunkSize == "0") {
		result["uplink-chunk-size"] = "3000-4000"
	}
	if enabled, _ := parseBooleanish(result["x-padding-obfs-mode"]); enabled {
		for _, field := range []string{"x-padding-key", "x-padding-header", "x-padding-placement", "x-padding-method"} {
			if value, _ := result[field].(string); value == "" {
				result[field] = xhttpDownloadSharedDefault(field)
			}
		}
	}
	return result, nil
}

func xhttpRangeMin(value any) int {
	text := jsTrimSpace(fmt.Sprint(value))
	if text == "" {
		return 0
	}
	part := strings.Split(text, "-")[0]
	parsed, _ := strconv.Atoi(strings.TrimPrefix(part, "+"))
	return parsed
}

func xhttpRangeMax(value any) int {
	text := jsTrimSpace(fmt.Sprint(value))
	if text == "" {
		return 0
	}
	parts := strings.Split(text, "-")
	parsed, _ := strconv.Atoi(strings.TrimPrefix(parts[len(parts)-1], "+"))
	return parsed
}

func xhttpStringField(source map[string]any, key, fallback string) (string, error) {
	value, exists := source[key]
	if !exists {
		return fallback, nil
	}
	text, ok := value.(string)
	if !ok {
		return "", fmt.Errorf("XHTTP %s needs string", key)
	}
	return text, nil
}
