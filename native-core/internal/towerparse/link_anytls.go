// Ported from SubBoost packages/core/src/parser/protocols/anytls.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"cmp"
	"fmt"
	"net/url"
	"regexp"
	"strings"
)

var anyTLSBase64TokenPattern = regexp.MustCompile(`^[A-Za-z0-9+/=_-]+$`)

func parseAnyTLSLink(raw string) (map[string]any, error) {
	if !strings.HasPrefix(raw, "anytls://") {
		return nil, fmt.Errorf("无效的 AnyTLS 链接")
	}
	normalized, encodedUserInfo, err := normalizeAnyTLSUserInfo(raw)
	if err != nil {
		return nil, err
	}
	parsed, query, err := linkURL(normalized)
	if err != nil {
		return nil, err
	}
	password := linkCredential(
		parsed,
		query,
		"password",
		"auth",
		"auth_str",
		"auth-str",
	)
	hasUserInfo := parsed.Username != "" || parsed.Password != ""
	if encodedUserInfo && hasUserInfo {
		if _, suffix, found := strings.CutLast(password, ":"); found {
			password = suffix
		}
	}
	server := parsed.Hostname
	if password == "" || server == "" {
		return nil, fmt.Errorf("AnyTLS 配置缺少必要字段")
	}
	port, err := linkPort(parsed.Port, 443)
	if err != nil {
		return nil, fmt.Errorf("无效的端口号")
	}
	if err := validateAnyTLSOptions(query); err != nil {
		return nil, err
	}

	udp := true
	switch strings.ToLower(jsTrimSpace(query.Get("udp"))) {
	case "0", "false":
		udp = false
	}
	// 上游 anytls.ts:164 的默认名是 AnyTLS-host:port，IPv6 不加方括号。
	name := linkName(
		parsed.Fragment,
		linkName(pickQueryParam(query, "remarks", "remark"), fmt.Sprintf("AnyTLS-%s:%d", server, port)),
	)
	sni := cmp.Or(pickQueryParam(
		query,
		"sni",
		"peer",
		"serverName",
		"servername",
	), server)
	node := map[string]any{
		"name": name, "type": "anytls", "server": server, "port": port,
		"password": password, "udp": udp, "sni": sni,
	}
	if query.Has("ech") {
		node["ech-opts"] = linkECHOpts(query.Get("ech"))
	}
	if linkStrictTrue(
		query,
		"allowInsecure",
		"allow_insecure",
		"insecure",
	) {
		node["skip-cert-verify"] = true
	}
	if fp := jsTrimSpace(pickQueryParam(
		query,
		"fp",
		"fingerprint",
		"client-fingerprint",
		"clientFingerprint",
	)); fp != "" {
		node["client-fingerprint"] = fp
	}
	addLinkAlpn(node, query.Get("alpn"))
	addLinkPinnedCertificate(node, query)
	if pqv := query.Get("pqv"); pqv != "" {
		node["pqv"] = pqv
	}
	for _, field := range []linkQueryAlias{
		{target: "idle-session-check-interval", keys: []string{"idle-session-check-interval", "idleSessionCheckInterval"}},
		{target: "idle-session-timeout", keys: []string{"idle-session-timeout", "idleSessionTimeout"}},
		{target: "min-idle-session", keys: []string{"min-idle-session", "minIdleSession"}},
	} {
		if value, ok := linkIntegerPrefix(query, field.keys...); ok {
			node[field.target] = value
		}
	}
	padding := pickQueryParam(
		query,
		"padding-scheme",
		"paddingScheme",
		"padding_scheme",
	)
	if padding = jsTrimSpace(padding); padding != "" {
		node["padding-scheme"] = padding
	}
	return node, nil
}

func normalizeAnyTLSUserInfo(raw string) (string, bool, error) {
	body := jsTrimSpace(strings.TrimPrefix(raw, "anytls://"))
	authority, _, _ := strings.Cut(body, "#")
	authority, _, _ = strings.Cut(authority, "?")
	if strings.Contains(strings.TrimSuffix(authority, "/"), "@") {
		return raw, false, nil
	}
	token, suffix, hasQuery := strings.Cut(body, "?")
	token = jsTrimSpace(token)
	if !hasQuery || !anyTLSBase64TokenPattern.MatchString(token) {
		return raw, false, nil
	}
	// SubBoost's Node Buffer stops at padding and ignores a final incomplete sextet.
	token, _, _ = strings.Cut(token, "=")
	token = strings.ReplaceAll(strings.ReplaceAll(token, "-", "+"), "_", "/")
	if len(token)%4 == 1 {
		token = token[:len(token)-1]
	}
	decoded, err := decodeLinkText(token)
	if err != nil {
		return "", false, err
	}
	return "anytls://" + decoded + "?" + suffix, true, nil
}

func validateAnyTLSOptions(query url.Values) error {
	security := query.Get("security")
	switch strings.ToLower(jsTrimSpace(security)) {
	case "", "tls", "none":
	case "reality":
		return fmt.Errorf("AnyTLS 不支持 security=reality / reality-opts（Mihomo 不支持）")
	default:
		return fmt.Errorf("AnyTLS 不支持 security=%s", security)
	}
	transport := jsTrimSpace(pickQueryParam(query, "type", "network", "transport"))
	switch strings.ToLower(transport) {
	case "", "tcp", "none":
	default:
		return fmt.Errorf("AnyTLS 不支持 type=%s（Mihomo 仅支持纯 TCP）", transport)
	}
	header := strings.ToLower(jsTrimSpace(pickQueryParam(query, "headerType", "header_type", "header-type")))
	if header != "" && header != "none" {
		return fmt.Errorf("AnyTLS 不支持 headerType=%s（仅支持纯 TCP）", header)
	}
	for _, group := range []struct {
		keys    []string
		message string
	}{
		{
			keys: []string{
				"pbk", "sid", "spx", "public-key", "public_key", "publicKey", "short-id", "short_id", "shortId",
			},
			message: "AnyTLS 不支持 Reality 参数: %s",
		},
		{
			keys:    []string{"serviceName", "service_name", "service-name", "authority", "host", "path", "seed", "mode"},
			message: "AnyTLS 不支持传输层参数: %s（Mihomo 仅支持纯 TCP）",
		},
	} {
		found := []string{}
		for _, key := range group.keys {
			if query.Has(key) {
				found = append(found, key)
			}
		}
		if len(found) > 0 {
			return fmt.Errorf(group.message, strings.Join(found, ", "))
		}
	}
	return nil
}
