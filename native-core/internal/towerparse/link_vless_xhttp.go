// Ported from SubBoost packages/core/src/parser/protocols/vless-xhttp.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"cmp"
	"encoding/json"
	"fmt"
	"maps"
	"net/url"
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

// parseShadowrocketXHTTPObfsParam 对应上游同名函数：不是 JSON 对象时整串当作 host；
// 是 JSON 对象时取第一个非空 Host 作为 host，其余字符串值作为请求头。
func parseShadowrocketXHTTPObfsParam(raw string) (string, map[string]any) {
	value := jsTrimSpace(raw)
	if value == "" {
		return "", nil
	}
	object, ok := parseJSONObject(value)
	if !ok {
		return value, nil
	}
	host := ""
	headers := map[string]any{}
	for _, rawKey := range jsonObjectKeys(value) {
		item, isString := object[rawKey].(string)
		key := jsTrimSpace(rawKey)
		if key == "" || !isString {
			continue
		}
		if strings.EqualFold(key, "host") {
			if host == "" {
				host = jsTrimSpace(item)
			}
			continue
		}
		headers[key] = item
	}
	if len(headers) == 0 {
		headers = nil
	}
	return host, headers
}

// jsonObjectKeys 按 JSON 文本中的出现顺序返回顶层键，用于复现上游 Object.entries 的遍历顺序。
// 调用前已确认 value 是合法 JSON 对象。
func jsonObjectKeys(value string) []string {
	decoder := json.NewDecoder(strings.NewReader(value))
	decoder.Token() // 跳过开头的 {
	keys := []string{}
	for decoder.More() {
		token, _ := decoder.Token()
		keys = append(keys, token.(string))
		var skip json.RawMessage
		decoder.Decode(&skip)
	}
	return keys
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
	obfsHost, obfsHeaders := parseShadowrocketXHTTPObfsParam(query.Get("obfsParam"))
	if value := cmp.Or(linkString(query, "xhttp-host", "xhttp_host", "xhttpHost", "host"), obfsHost, host); value != "" {
		result["host"] = value
	}
	if value := linkString(query, "xhttp-mode", "xhttp_mode", "xhttpMode", "mode"); value != "" {
		result["mode"] = value
	}
	// 显式请求头覆盖 obfsParam 里的同名请求头。
	explicitHeaders := linkHeaderMap(linkString(query, "xhttp-headers", "xhttp_headers", "xhttpHeaders", "headers"))
	if obfsHeaders != nil || explicitHeaders != nil {
		headers := map[string]any{}
		maps.Copy(headers, obfsHeaders)
		maps.Copy(headers, explicitHeaders)
		result["headers"] = headers
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
