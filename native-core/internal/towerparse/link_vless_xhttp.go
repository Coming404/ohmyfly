// Ported from SubBoost packages/core/src/parser/protocols/vless-xhttp.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"encoding/json"
	"fmt"
	"math"
	"net/url"
	"strconv"
	"strings"
)

func applyXHTTPTransport(node map[string]any, path, host string, query url.Values) error {
	node["network"] = "xhttp"
	legacy := buildXHTTPLegacyOptions(path, host, query)
	options, err := mergeXHTTPExtra(query, legacy)
	if err != nil {
		return err
	}
	node["xhttp-opts"] = options
	return nil
}

func buildXHTTPLegacyOptions(path, host string, query url.Values) map[string]any {
	result := map[string]any{}
	if value := linkString(query, "xhttp-path", "xhttp_path", "xhttpPath", "path"); value != "" {
		result["path"] = value
	} else if path != "" {
		result["path"] = path
	} else {
		result["path"] = "/"
	}
	if value := linkString(query, "xhttp-host", "xhttp_host", "xhttpHost", "host"); value != "" {
		result["host"] = value
	} else if host != "" {
		result["host"] = host
	}
	if value := linkString(query, "xhttp-mode", "xhttp_mode", "xhttpMode", "mode"); value != "" {
		result["mode"] = value
	}
	if value := linkString(query, "xhttp-headers", "xhttp_headers", "xhttpHeaders", "headers"); value != "" {
		if headers := linkHeaderMap(value); headers != nil {
			result["headers"] = headers
		}
	}
	if value, ok := linkBoolValue(query, "no-grpc-header", "no_grpc_header", "noGrpcHeader"); ok {
		result["no-grpc-header"] = value
	}
	for target, keys := range map[string][]string{
		"x-padding-bytes":        {"x-padding-bytes", "x_padding_bytes", "xPaddingBytes"},
		"sc-max-each-post-bytes": {"sc-max-each-post-bytes", "sc_max_each_post_bytes", "scMaxEachPostBytes"},
	} {
		if value := linkString(query, keys...); value != "" {
			result[target] = value
		}
	}
	reuse := map[string]any{}
	for target, keys := range map[string][]string{
		"max-concurrency":     {"max-concurrency", "max_concurrency", "maxConcurrency"},
		"max-connections":     {"max-connections", "max_connections", "maxConnections"},
		"c-max-reuse-times":   {"c-max-reuse-times", "c_max_reuse_times", "cMaxReuseTimes"},
		"h-max-request-times": {"h-max-request-times", "h_max_request_times", "hMaxRequestTimes"},
		"h-max-reusable-secs": {"h-max-reusable-secs", "h_max_reusable_secs", "hMaxReusableSecs"},
	} {
		if value := linkString(query, keys...); value != "" {
			reuse[target] = value
		}
	}
	if len(reuse) > 0 {
		result["reuse-settings"] = reuse
	}
	download := map[string]any{}
	if value := linkString(query, "download-path", "download_path", "downloadPath"); value != "" {
		download["path"] = value
	}
	if value := linkString(query, "download-host", "download_host", "downloadHost"); value != "" {
		download["host"] = value
	}
	if value := linkString(query, "download-headers", "download_headers", "downloadHeaders"); value != "" {
		if headers := linkHeaderMap(value); headers != nil {
			download["headers"] = headers
		}
	}
	if len(download) > 0 {
		result["download-settings"] = download
	}
	return result
}

func mergeXHTTPExtra(query url.Values, legacy map[string]any) (map[string]any, error) {
	_, hasExtra := query["extra"]
	_, hasDownload := query["downloadSettings"]
	isSource := hasExtra || hasDownload
	result := map[string]any{}
	var extra map[string]any
	if hasExtra {
		var err error
		extra, err = parseXHTTPJSONObject(query.Get("extra"), "extra")
		if err != nil {
			return nil, err
		}
		mapped, err := mapXHTTPExtra(extra, false)
		if err != nil {
			return nil, err
		}
		result = mapped
	}
	mergeXHTTPMap(result, legacy)
	applyXHTTPQueryAliases(result, query)
	if isSource {
		var err error
		result, err = applyXHTTPSourceDefaults(result)
		if err != nil {
			return nil, err
		}
	}
	path, _ := result["path"].(string)
	if hasExtra && strings.Contains(path, "?") {
		return nil, fmt.Errorf("XHTTP path query parameters cannot be represented")
	}
	if hasExtra {
		placement := result["uplink-data-placement"]
		method := result["uplink-http-method"]
		if result["mode"] != "packet-up" && (placement == "header" || placement == "cookie" || method == "GET") {
			return nil, fmt.Errorf("XHTTP header/cookie upload and GET require packet-up")
		}
	}
	download, hasDownloadSettings := extra["downloadSettings"]
	if hasDownload {
		var err error
		download, err = parseXHTTPJSONObject(query.Get("downloadSettings"), "downloadSettings")
		if err != nil {
			return nil, err
		}
		hasDownloadSettings = true
	}
	if hasDownloadSettings {
		mapped, err := mapXHTTPDownload(download, result)
		if err != nil {
			return nil, err
		}
		if legacyDownload, ok := legacy["download-settings"].(map[string]any); ok {
			mergeXHTTPMap(mapped, legacyDownload)
		}
		result["download-settings"] = mapped
	}
	if result["mode"] == "stream-one" && result["download-settings"] != nil {
		return nil, fmt.Errorf("XHTTP stream-one does not support downloadSettings")
	}
	return normalizeXHTTPFields(result)
}

func parseXHTTPJSONObject(raw, label string) (map[string]any, error) {
	var result map[string]any
	if err := json.Unmarshal([]byte(raw), &result); err != nil || result == nil {
		return nil, fmt.Errorf("XHTTP %s needs a JSON object", label)
	}
	return result, nil
}

func mergeXHTTPMap(target, source map[string]any) {
	for key, value := range source {
		if key == "headers" {
			if incoming, ok := value.(map[string]any); ok {
				merged, _ := target["headers"].(map[string]any)
				if merged == nil {
					merged = map[string]any{}
				}
				for header, item := range incoming {
					merged[header] = item
				}
				target[key] = merged
				continue
			}
		}
		if key == "reuse-settings" {
			if incoming, ok := value.(map[string]any); ok {
				merged, _ := target[key].(map[string]any)
				if merged == nil {
					merged = map[string]any{}
				}
				for field, item := range incoming {
					merged[field] = item
				}
				target[key] = merged
				continue
			}
		}
		target[key] = value
	}
}

// xhttpExtraFields and xhttpReuseFields mirror upstream EXTRA_FIELDS and REUSE_FIELDS.
var xhttpExtraFields = map[string]string{
	"headers": "headers", "noGRPCHeader": "no-grpc-header", "xPaddingBytes": "x-padding-bytes",
	"xPaddingObfsMode": "x-padding-obfs-mode", "xPaddingKey": "x-padding-key", "xPaddingHeader": "x-padding-header",
	"xPaddingPlacement": "x-padding-placement", "xPaddingMethod": "x-padding-method", "uplinkHTTPMethod": "uplink-http-method",
	"sessionIDPlacement": "session-placement", "sessionIDKey": "session-key", "sessionIDTable": "session-table",
	"sessionIDLength": "session-length", "seqPlacement": "seq-placement", "seqKey": "seq-key",
	"uplinkDataPlacement": "uplink-data-placement", "uplinkDataKey": "uplink-data-key", "uplinkChunkSize": "uplink-chunk-size",
	"scMaxEachPostBytes": "sc-max-each-post-bytes", "scMinPostsIntervalMs": "sc-min-posts-interval-ms",
}

var xhttpReuseFields = map[string]string{
	"maxConcurrency": "max-concurrency", "maxConnections": "max-connections", "cMaxReuseTimes": "c-max-reuse-times",
	"hMaxRequestTimes": "h-max-request-times", "hMaxReusableSecs": "h-max-reusable-secs", "hKeepAlivePeriod": "h-keep-alive-period",
}

// applyXHTTPQueryAliases accepts each field as kebab-case, snake_case, or its extra key; explicit query fields win.
func applyXHTTPQueryAliases(options map[string]any, query url.Values) {
	for _, fields := range []map[string]string{xhttpExtraFields, xhttpReuseFields} {
		for source, target := range fields {
			if target == "headers" || target == "no-grpc-header" {
				continue
			}
			value := ""
			for _, key := range []string{target, strings.ReplaceAll(target, "-", "_"), source} {
				if value = query.Get(key); value != "" {
					break
				}
			}
			if value == "" {
				continue
			}
			if _, isReuse := xhttpReuseFields[source]; !isReuse {
				options[target] = value
				continue
			}
			reuse, _ := options["reuse-settings"].(map[string]any)
			if reuse == nil {
				reuse = map[string]any{}
			}
			reuse[target] = value
			options["reuse-settings"] = reuse
		}
	}
	// Raw like upstream; normalizeXHTTPFields rejects non-boolean values. Other spellings are legacy.
	if _, ok := query["noGRPCHeader"]; ok {
		options["no-grpc-header"] = query.Get("noGRPCHeader")
	}
}

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

func mapXHTTPDownload(value any, main map[string]any) (map[string]any, error) {
	source, ok := value.(map[string]any)
	if !ok || source == nil {
		return nil, fmt.Errorf("XHTTP downloadSettings needs object")
	}
	network, err := xhttpStringField(source, "network", "xhttp")
	if err != nil {
		return nil, err
	}
	if network != "xhttp" && network != "splithttp" {
		return nil, fmt.Errorf("XHTTP download network unsupported")
	}
	server, err := xhttpStringField(source, "address", "")
	if err != nil {
		return nil, err
	}
	server = jsTrimSpace(server)
	portValue, hasPort := source["port"]
	port, validPort := xhttpIntegerValue(portValue)
	if text, ok := portValue.(string); ok {
		for _, digit := range text {
			if digit < '0' || digit > '9' {
				validPort = false
				break
			}
		}
	}
	if server == "" || !hasPort || !validPort || port < 1 || port > 65535 {
		return nil, fmt.Errorf("XHTTP download address or port invalid")
	}
	security, err := xhttpStringField(source, "security", "none")
	if err != nil {
		return nil, err
	}
	if security != "" && security != "none" && security != "tls" && security != "reality" {
		return nil, fmt.Errorf("XHTTP download security unsupported")
	}
	for _, field := range []string{"sockopt", "finalmask"} {
		if _, exists := source[field]; exists {
			return nil, fmt.Errorf("XHTTP download %s unsupported", field)
		}
	}
	transport := map[string]any{}
	if raw := source["xhttpSettings"]; raw != nil {
		transport, ok = raw.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("XHTTP download xhttpSettings needs object")
		}
	} else if raw := source["splithttpSettings"]; raw != nil {
		transport, ok = raw.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("XHTTP download splithttpSettings needs object")
		}
	}
	effective := transport
	if raw, exists := transport["extra"]; exists {
		effective, ok = raw.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("XHTTP download extra needs object")
		}
	}
	mapped, err := mapXHTTPExtra(effective, true)
	if err != nil {
		return nil, err
	}
	if _, exists := effective["downloadSettings"]; exists {
		return nil, fmt.Errorf("XHTTP nested downloadSettings unsupported")
	}
	for _, shared := range xhttpDownloadSharedDefaults {
		if xhttpEffectiveShared(mapped, shared.key, shared.fallback) != xhttpEffectiveShared(main, shared.key, shared.fallback) {
			return nil, fmt.Errorf("XHTTP independent download %s unsupported", shared.key)
		}
	}
	securitySettings := map[string]any{}
	ok = true
	if security == "reality" {
		if raw, exists := source["realitySettings"]; exists && raw != nil {
			securitySettings, ok = raw.(map[string]any)
		} else {
			securitySettings = map[string]any{}
		}
	} else if raw, exists := source["tlsSettings"]; exists && raw != nil {
		securitySettings, ok = raw.(map[string]any)
	}
	if !ok {
		return nil, fmt.Errorf("XHTTP download security settings need object")
	}
	for _, field := range []string{"echConfigList", "echForceQuery", "echServerKeys", "echSockopt", "mldsa65Verify", "pinnedPeerCertSha256", "verifyPeerCertByName", "pinnedPeerCertificateChainSha256", "certificates", "verifyPeerCertInNames", "disableSystemRoot", "useSystemCA", "minVersion", "maxVersion", "cipherSuites", "curvePreferences", "enableSessionResumption"} {
		if item := securitySettings[field]; item != nil && item != false && item != "" {
			if list, ok := item.([]any); ok && len(list) == 0 {
				continue
			}
			return nil, fmt.Errorf("XHTTP download %s unsupported", field)
		}
	}
	path, err := xhttpStringField(transport, "path", "/")
	if err != nil {
		return nil, err
	}
	if path == "" {
		path = "/"
	}
	if strings.Contains(path, "?") {
		return nil, fmt.Errorf("XHTTP download path query unsupported")
	}
	alpn := []any{}
	if raw := securitySettings["alpn"]; raw != nil {
		list, ok := raw.([]any)
		if !ok {
			return nil, fmt.Errorf("XHTTP download alpn needs string array")
		}
		for _, item := range list {
			if _, ok := item.(string); !ok {
				return nil, fmt.Errorf("XHTTP download alpn needs string array")
			}
		}
		alpn = list
	}
	allowInsecure := false
	if raw, exists := securitySettings["allowInsecure"]; exists {
		parsed, ok := raw.(bool)
		if !ok {
			return nil, fmt.Errorf("XHTTP download allowInsecure needs boolean")
		}
		allowInsecure = parsed
	}
	serverName, err := xhttpStringField(securitySettings, "serverName", "")
	if err != nil {
		return nil, err
	}
	fingerprintDefault := ""
	if security == "reality" {
		fingerprintDefault = "chrome"
	}
	fingerprint, err := xhttpStringField(securitySettings, "fingerprint", fingerprintDefault)
	if err != nil {
		return nil, err
	}
	host, err := xhttpStringField(transport, "host", "")
	if err != nil {
		return nil, err
	}
	result := map[string]any{
		"server":             server,
		"port":               port,
		"tls":                security == "tls" || security == "reality",
		"servername":         serverName,
		"client-fingerprint": fingerprint,
		"skip-cert-verify":   allowInsecure,
		"alpn":               alpn,
		"ech-opts":           map[string]any{"enable": false},
		"path":               path,
		"host":               host,
		"headers":            map[string]any{},
		"reuse-settings":     mapped["reuse-settings"],
		"reality-opts":       map[string]any{"public-key": ""},
	}
	if security == "reality" {
		// Upstream: normalizeMihomoRealityPublicKey(tls.password || tls.publicKey).
		rawKey := securitySettings["password"]
		if !xhttpJSTruthy(rawKey) {
			rawKey = securitySettings["publicKey"]
		}
		publicKey, _ := rawKey.(string)
		publicKey = jsTrimSpace(publicKey)
		if !isRealityPublicKey(publicKey) {
			return nil, fmt.Errorf("XHTTP download Reality publicKey invalid")
		}
		reality := map[string]any{"public-key": publicKey}
		if raw, exists := securitySettings["shortId"]; exists {
			// Upstream normalizeRealityShortId drops values that are not strings or numbers.
			text, _ := xhttpIntegerText(raw)
			if shortID := normalizeRealityShortID(text); shortID != "" {
				reality["short-id"] = shortID
			}
		}
		if raw, exists := securitySettings["support-x25519mlkem768"]; exists {
			value, ok := raw.(bool)
			if !ok {
				return nil, fmt.Errorf("XHTTP download Reality support flag needs boolean")
			}
			reality["support-x25519mlkem768"] = value
		}
		result["reality-opts"] = reality
	}
	if headers, ok := mapped["headers"].(map[string]any); ok {
		result["headers"] = headers
	}
	return result, nil
}

// xhttpDownloadSharedDefaults mirrors upstream DOWNLOAD_SHARED_DEFAULTS in upstream order:
// download GET requests use these fields, but Mihomo cannot override them per connection.
var xhttpDownloadSharedDefaults = []struct {
	key      string
	fallback any
}{
	{"x-padding-bytes", "100-1000"}, {"x-padding-obfs-mode", false},
	{"x-padding-key", "x_padding"}, {"x-padding-header", "X-Padding"}, {"x-padding-placement", "queryInHeader"},
	{"x-padding-method", "repeat-x"}, {"session-placement", "path"}, {"session-key", ""}, {"seq-placement", "path"},
}

func xhttpDownloadSharedDefault(key string) any {
	for _, item := range xhttpDownloadSharedDefaults {
		if item.key == key {
			return item.fallback
		}
	}
	return nil
}

// xhttpEffectiveShared mirrors upstream effectiveSharedField.
func xhttpEffectiveShared(options map[string]any, key string, fallback any) any {
	if strings.HasPrefix(key, "x-padding-") && key != "x-padding-bytes" && key != "x-padding-obfs-mode" {
		if enabled, _ := parseBooleanish(options["x-padding-obfs-mode"]); !enabled {
			return fallback
		}
	}
	if key == "session-key" {
		if value, _ := options[key].(string); value == "" {
			switch options["session-placement"] {
			case "header":
				return "X-Session"
			case "cookie", "query":
				return "x_session"
			}
		}
	}
	if value, ok := options[key]; ok && value != "" {
		return value
	}
	return fallback
}

// xhttpJSTruthy reports JavaScript truthiness for JSON-decoded values.
func xhttpJSTruthy(value any) bool {
	switch value := value.(type) {
	case nil:
		return false
	case bool:
		return value
	case string:
		return value != ""
	case float64:
		return value != 0 && !math.IsNaN(value)
	default:
		return true
	}
}
