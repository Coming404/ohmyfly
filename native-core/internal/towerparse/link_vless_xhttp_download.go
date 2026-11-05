// Ported from SubBoost packages/core/src/parser/protocols/vless-xhttp.ts @4015347 (AGPL-3.0-only).
// XHTTP downloadSettings 映射：独立下载连接的地址、TLS/Reality 与共享字段校验。
package towerparse

import (
	"fmt"
	"math"
	"strings"
)

// mapXHTTPDownload 对应上游 downloadSettings 映射：校验独立下载连接，转换成 mihomo 的 download-settings。
// 各步骤按上游的检查顺序执行，多个字段都不合法时返回的错误与拆分前相同。
func mapXHTTPDownload(value any, main map[string]any) (map[string]any, error) {
	source, ok := value.(map[string]any)
	if !ok || source == nil {
		return nil, fmt.Errorf("XHTTP downloadSettings needs object")
	}
	server, port, security, err := xhttpDownloadEndpoint(source)
	if err != nil {
		return nil, err
	}
	transport, mapped, err := xhttpDownloadTransport(source, main)
	if err != nil {
		return nil, err
	}
	settings, err := xhttpDownloadSecuritySettings(source, security)
	if err != nil {
		return nil, err
	}
	result, err := xhttpDownloadResult(transport, settings, security)
	if err != nil {
		return nil, err
	}
	result["server"] = server
	result["port"] = port
	result["reuse-settings"] = mapped["reuse-settings"]
	if security == "reality" {
		reality, err := xhttpDownloadReality(settings)
		if err != nil {
			return nil, err
		}
		result["reality-opts"] = reality
	}
	if headers, ok := mapped["headers"].(map[string]any); ok {
		result["headers"] = headers
	}
	return result, nil
}

// xhttpDownloadEndpoint 校验 network、address、port、security，并拒绝 sockopt、finalmask。
func xhttpDownloadEndpoint(source map[string]any) (string, int, string, error) {
	network, err := xhttpStringField(source, "network", "xhttp")
	if err != nil {
		return "", 0, "", err
	}
	if network != "xhttp" && network != "splithttp" {
		return "", 0, "", fmt.Errorf("XHTTP download network unsupported")
	}
	server, err := xhttpStringField(source, "address", "")
	if err != nil {
		return "", 0, "", err
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
		return "", 0, "", fmt.Errorf("XHTTP download address or port invalid")
	}
	security, err := xhttpStringField(source, "security", "none")
	if err != nil {
		return "", 0, "", err
	}
	if security != "" && security != "none" && security != "tls" && security != "reality" {
		return "", 0, "", fmt.Errorf("XHTTP download security unsupported")
	}
	for _, field := range []string{"sockopt", "finalmask"} {
		if _, exists := source[field]; exists {
			return "", 0, "", fmt.Errorf("XHTTP download %s unsupported", field)
		}
	}
	return server, port, security, nil
}

// xhttpDownloadTransport 取 xhttpSettings（没有时取 splithttpSettings）及其 extra 并映射。
// mihomo 不能为下载连接单独设置共享字段，所以这些字段必须与主连接一致。
func xhttpDownloadTransport(source, main map[string]any) (map[string]any, map[string]any, error) {
	transport := map[string]any{}
	var ok bool
	if raw := source["xhttpSettings"]; raw != nil {
		if transport, ok = raw.(map[string]any); !ok {
			return nil, nil, fmt.Errorf("XHTTP download xhttpSettings needs object")
		}
	} else if raw := source["splithttpSettings"]; raw != nil {
		if transport, ok = raw.(map[string]any); !ok {
			return nil, nil, fmt.Errorf("XHTTP download splithttpSettings needs object")
		}
	}
	effective := transport
	if raw, exists := transport["extra"]; exists {
		if effective, ok = raw.(map[string]any); !ok {
			return nil, nil, fmt.Errorf("XHTTP download extra needs object")
		}
	}
	mapped, err := mapXHTTPExtra(effective, true)
	if err != nil {
		return nil, nil, err
	}
	if _, exists := effective["downloadSettings"]; exists {
		return nil, nil, fmt.Errorf("XHTTP nested downloadSettings unsupported")
	}
	for _, shared := range xhttpDownloadSharedDefaults {
		if xhttpEffectiveShared(mapped, shared.key, shared.fallback) != xhttpEffectiveShared(main, shared.key, shared.fallback) {
			return nil, nil, fmt.Errorf("XHTTP independent download %s unsupported", shared.key)
		}
	}
	return transport, mapped, nil
}

// xhttpDownloadUnsupportedSecurity 列出 mihomo 下载连接无法表达的 TLS/Reality 设置，出现非空值时整条拒绝。
var xhttpDownloadUnsupportedSecurity = []string{
	"echConfigList", "echForceQuery", "echServerKeys", "echSockopt", "mldsa65Verify",
	"pinnedPeerCertSha256", "verifyPeerCertByName", "pinnedPeerCertificateChainSha256", "certificates",
	"verifyPeerCertInNames", "disableSystemRoot", "useSystemCA", "minVersion", "maxVersion",
	"cipherSuites", "curvePreferences", "enableSessionResumption",
}

// xhttpDownloadSecuritySettings 按 security 取 realitySettings 或 tlsSettings，缺省为空对象。
func xhttpDownloadSecuritySettings(source map[string]any, security string) (map[string]any, error) {
	key := "tlsSettings"
	if security == "reality" {
		key = "realitySettings"
	}
	settings := map[string]any{}
	ok := true
	if raw, exists := source[key]; exists && raw != nil {
		settings, ok = raw.(map[string]any)
	}
	if !ok {
		return nil, fmt.Errorf("XHTTP download security settings need object")
	}
	for _, field := range xhttpDownloadUnsupportedSecurity {
		if item := settings[field]; item != nil && item != false && item != "" {
			if list, ok := item.([]any); ok && len(list) == 0 {
				continue
			}
			return nil, fmt.Errorf("XHTTP download %s unsupported", field)
		}
	}
	return settings, nil
}

// xhttpDownloadResult 读取 path、alpn、allowInsecure、serverName、fingerprint、host，生成下载连接的基础字段。
func xhttpDownloadResult(transport, settings map[string]any, security string) (map[string]any, error) {
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
	alpn, err := xhttpDownloadALPN(settings)
	if err != nil {
		return nil, err
	}
	allowInsecure := false
	if raw, exists := settings["allowInsecure"]; exists {
		parsed, ok := raw.(bool)
		if !ok {
			return nil, fmt.Errorf("XHTTP download allowInsecure needs boolean")
		}
		allowInsecure = parsed
	}
	serverName, err := xhttpStringField(settings, "serverName", "")
	if err != nil {
		return nil, err
	}
	fingerprintDefault := ""
	if security == "reality" {
		fingerprintDefault = "chrome"
	}
	fingerprint, err := xhttpStringField(settings, "fingerprint", fingerprintDefault)
	if err != nil {
		return nil, err
	}
	host, err := xhttpStringField(transport, "host", "")
	if err != nil {
		return nil, err
	}
	return map[string]any{
		"tls":                security == "tls" || security == "reality",
		"servername":         serverName,
		"client-fingerprint": fingerprint,
		"skip-cert-verify":   allowInsecure,
		"alpn":               alpn,
		"ech-opts":           map[string]any{"enable": false},
		"path":               path,
		"host":               host,
		"headers":            map[string]any{},
		"reality-opts":       map[string]any{"public-key": ""},
	}, nil
}

// xhttpDownloadALPN 要求 alpn 是字符串数组，缺省为空数组。
func xhttpDownloadALPN(settings map[string]any) ([]any, error) {
	raw := settings["alpn"]
	if raw == nil {
		return []any{}, nil
	}
	list, ok := raw.([]any)
	if !ok {
		return nil, fmt.Errorf("XHTTP download alpn needs string array")
	}
	for _, item := range list {
		if _, ok := item.(string); !ok {
			return nil, fmt.Errorf("XHTTP download alpn needs string array")
		}
	}
	return list, nil
}

// xhttpDownloadReality 对应上游 normalizeMihomoRealityPublicKey(tls.password || tls.publicKey) 及 shortId、support 标志处理。
func xhttpDownloadReality(settings map[string]any) (map[string]any, error) {
	rawKey := settings["password"]
	if !xhttpJSTruthy(rawKey) {
		rawKey = settings["publicKey"]
	}
	publicKey, _ := rawKey.(string)
	publicKey = jsTrimSpace(publicKey)
	if !isRealityPublicKey(publicKey) {
		return nil, fmt.Errorf("XHTTP download Reality publicKey invalid")
	}
	reality := map[string]any{"public-key": publicKey}
	if raw, exists := settings["shortId"]; exists {
		// 上游 normalizeRealityShortId 丢弃既不是字符串也不是数字的值。
		text, _ := xhttpIntegerText(raw)
		if shortID := normalizeRealityShortID(text); shortID != "" {
			reality["short-id"] = shortID
		}
	}
	if raw, exists := settings["support-x25519mlkem768"]; exists {
		value, ok := raw.(bool)
		if !ok {
			return nil, fmt.Errorf("XHTTP download Reality support flag needs boolean")
		}
		reality["support-x25519mlkem768"] = value
	}
	return reality, nil
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
