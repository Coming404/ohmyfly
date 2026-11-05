// Ported from SubBoost packages/core/src/parser/protocols/simple-proxy.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"encoding/json"
	"fmt"
	"net/url"
	"regexp"
	"strconv"
	"strings"
)

var simpleProxyIPv6SuffixPattern = regexp.MustCompile(`^[a-zA-Z][a-zA-Z0-9+.-]*://(?:[^/?#@]+@)?\[[^\]]+\]$`)

func parseSocksLink(raw string) (map[string]any, error) {
	raw = jsTrimSpace(raw)
	match := linkSchemePattern.FindStringSubmatch(raw)
	if match == nil {
		return nil, fmt.Errorf("无效的 SOCKS 链接")
	}
	switch strings.ToLower(match[1]) {
	case "socks", "socks4", "socks5", "socks5+tls":
		return parseSimpleProxyLink(raw)
	default:
		return nil, fmt.Errorf("无效的 SOCKS 链接")
	}
}

func parseHTTPProxyLink(raw string) (map[string]any, error) {
	return parseSimpleProxyLink(raw)
}

func parseSSHLink(raw string) (map[string]any, error) {
	return parseSimpleProxyLink(raw)
}

func parseSimpleProxyLink(raw string) (map[string]any, error) {
	raw = jsTrimSpace(raw)
	match := linkSchemePattern.FindStringSubmatch(raw)
	if match == nil {
		return nil, fmt.Errorf("无效的代理格式")
	}
	kind := strings.ToLower(match[1])
	forceTLS := kind == "socks5+tls"
	defaultPort := 0
	switch kind {
	case "http":
		defaultPort = 80
	case "https":
		defaultPort = 443
	case "socks", "socks4", "socks5", "socks5+tls":
		defaultPort = 1080
		if kind != "socks4" {
			kind = "socks5"
		}
		if forceTLS {
			defaultPort = 443
		}
	case "ssh":
		defaultPort = 22
	default:
		return nil, fmt.Errorf("不支持的协议: %s", kind)
	}

	raw, suffixName := stripSimpleProxyMeta(raw)
	body := raw[len(match[0]):]
	authority := body
	if index := strings.IndexAny(body, "/?#"); index != -1 {
		authority = body[:index]
	}
	_, fragment, _ := strings.Cut(raw, "#")
	if !strings.HasPrefix(authority, "[") && !strings.ContainsAny(authority, "@:") {
		if decoded, ok := decodeSimpleProxyToken(authority); ok {
			auth, serverPart := "", decoded
			if before, after, found := strings.CutLast(decoded, "@"); found {
				auth, serverPart = before, after
			}
			server, port, hasPort := simpleProxyServerPort(jsTrimSpace(serverPart))
			if server != "" && hasPort {
				query := simpleProxyQuery(raw)
				name := linkName(suffixName, linkName(fragment, linkName(
					pickQueryParam(query, "remark", "remarks"), simpleProxyDefaultName(kind, server, port),
				)))
				username, password, _ := strings.Cut(jsTrimSpace(auth), ":")
				node := buildSimpleProxyNode(kind, name, server, port, username, password, forceTLS)
				applySimpleProxyQuery(node, query)
				return node, nil
			}
		}
	}

	if !strings.HasPrefix(authority, "[") && !strings.Contains(authority, "@") {
		parts := strings.Split(authority, ":")
		if len(parts) >= 4 {
			server := parts[0]
			if server == "" {
				return nil, fmt.Errorf("缺少服务器地址")
			}
			port, err := linkPort(parts[1], 0)
			if err != nil || port == 0 {
				return nil, fmt.Errorf("无效的端口号")
			}
			name := linkName(suffixName, linkName(fragment, simpleProxyDefaultName(kind, server, port)))
			return buildSimpleProxyNode(kind, name, server, port,
				linkDecodeComponent(parts[2]), linkDecodeComponent(strings.Join(parts[3:], ":")), forceTLS), nil
		}
	}

	parsed, query, err := linkURL(raw)
	if err != nil {
		return nil, err
	}
	server := parsed.Hostname
	if server == "" {
		return nil, fmt.Errorf("缺少服务器地址")
	}
	port, err := linkPort(parsed.Port, defaultPort)
	if err != nil {
		return nil, fmt.Errorf("无效的端口号")
	}
	username, password := parsed.Username, parsed.Password
	if username != "" && password == "" && (kind == "socks4" || kind == "socks5") {
		if decoded, ok := decodeSimpleProxyToken(username); ok && strings.Contains(decoded, ":") && !strings.Contains(decoded, "@") {
			username, password, _ = strings.Cut(jsTrimSpace(decoded), ":")
		}
	}
	name := linkName(suffixName, linkName(parsed.Fragment, simpleProxyDefaultName(kind, server, port)))
	node := buildSimpleProxyNode(kind, name, server, port, username, password, forceTLS)
	applySimpleProxyQuery(node, query)
	return node, nil
}

// simpleProxyDefaultName 与上游一致：类型大写加 "-host:port"，IPv6 不加方括号；socks4、socks5 都用 SOCKS-。
// 有意偏离：上游 parseSocks 在解析后把等于 SOCKS5-host:port 的名称一律改成 SOCKS-，用户在 # 里显式写的也会改；
// 这里只在生成默认名时用 SOCKS-，保留用户写的名称。
func simpleProxyDefaultName(kind, server string, port int) string {
	if kind == "socks4" || kind == "socks5" {
		kind = "socks"
	}
	return strings.ToUpper(kind) + "-" + server + ":" + strconv.Itoa(port)
}

func stripSimpleProxyMeta(raw string) (string, string) {
	raw, name, _ := simpleProxyEnclosed(raw, "{", "}")
	withoutBracket, bracket, found := simpleProxyEnclosed(raw, "[", "]")
	ipv6 := strings.Contains(bracket, ":") && (strings.HasPrefix(raw, "[") || simpleProxyIPv6SuffixPattern.MatchString(raw))
	if found && !ipv6 {
		raw = withoutBracket
	}
	return raw, name
}

func simpleProxyEnclosed(raw, open, close string) (string, string, bool) {
	if !strings.HasSuffix(raw, close) {
		return raw, "", false
	}
	index := strings.LastIndex(raw, open)
	if index == -1 {
		return raw, "", false
	}
	value := raw[index+len(open) : len(raw)-len(close)]
	if strings.Contains(value, close) {
		return raw, "", false
	}
	return raw[:index], value, true
}

func decodeSimpleProxyToken(raw string) (string, bool) {
	raw = jsTrimSpace(raw)
	if !anyTLSBase64TokenPattern.MatchString(raw) {
		return "", false
	}
	// Match Node Buffer's padding and incomplete-sextet handling before using the shared decoder.
	raw, _, _ = strings.Cut(raw, "=")
	raw = strings.NewReplacer("-", "+", "_", "/").Replace(raw)
	if len(raw)%4 == 1 {
		raw = raw[:len(raw)-1]
	}
	decoded, err := decodeLinkBase64(raw)
	if err != nil {
		return "", false
	}
	text := strings.TrimPrefix(decodeUTF8Lossy(decoded), "\uFEFF")
	for _, char := range text {
		if char <= '\x08' || char >= '\x0e' && char <= '\x1f' {
			return "", false
		}
	}
	return text, text != ""
}

// This is only used inside scheme links; it is not a naked host:port entry point.
func simpleProxyServerPort(raw string) (string, int, bool) {
	server, portText, found := strings.CutLast(raw, ":")
	if strings.HasPrefix(raw, "[") {
		if host, tail, closed := strings.Cut(raw[1:], "]"); closed {
			server = host
			portText, found = strings.CutPrefix(tail, ":")
		}
	} else if !found {
		server = raw
	}
	if !found {
		return server, 0, false
	}
	// Upstream does not range-check ports in the encoded-authority branch.
	port, ok := linkIntegerPrefix(url.Values{"port": {portText}}, "port")
	return server, port, ok
}

func simpleProxyQuery(raw string) url.Values {
	_, query, found := strings.Cut(raw, "?")
	if !found {
		return nil
	}
	query, _, _ = strings.Cut(query, "#")
	return linkSearchParams(query)
}

func buildSimpleProxyNode(kind, name, server string, port int, username, password string, forceTLS bool) map[string]any {
	node := map[string]any{"name": name, "type": kind, "server": server, "port": port}
	switch kind {
	case "socks4", "socks5":
		node["udp"] = kind == "socks5"
		if forceTLS {
			node["tls"] = true
		}
	case "http", "https":
		node["tls"] = kind == "https"
	}
	if username != "" {
		node["username"] = username
	}
	if password != "" {
		node["password"] = password
	}
	return node
}

func applySimpleProxyQuery(node map[string]any, query url.Values) {
	if insecure, ok := linkOptionalBool(query, "skip-cert-verify", "skip_cert_verify", "allowInsecure", "allow_insecure", "allow-insecure", "insecure"); ok {
		node["skip-cert-verify"] = insecure
	}
	kind := node["type"].(string)
	if kind != "ssh" {
		if sni := jsTrimSpace(pickQueryParam(query, "sni", "peer", "tls-name", "tls_name")); sni != "" {
			node["sni"] = sni
		}
		if verified, ok := linkBool(query.Get("tls-verification")); ok {
			node["skip-cert-verify"] = !verified
		}
	}
	switch kind {
	case "http", "https":
		if headers := simpleProxyHeaders(pickQueryParam(query, "headers", "header")); headers != nil {
			node["headers"] = headers
		}
	case "socks4", "socks5":
		if udp, ok := linkBool(pickQueryParam(query, "udp", "udp-relay", "udp_relay")); ok {
			node["udp"] = udp
		}
	case "ssh":
		for _, field := range []linkQueryAlias{
			{target: "private-key", keys: []string{"private-key", "private_key", "privateKey"}},
			{target: "server-fingerprint", keys: []string{"server-fingerprint", "server_fingerprint"}},
		} {
			if value := jsTrimSpace(pickQueryParam(query, field.keys...)); value != "" {
				node[field.target] = value
			}
		}
		for _, field := range []linkQueryAlias{
			{target: "host-key", keys: []string{"host-key", "host_key", "hostKey"}},
			{target: "host-key-algorithms", keys: []string{"host-key-algorithms", "host_key_algorithms"}},
		} {
			if value := jsTrimSpace(pickQueryParam(query, field.keys...)); value != "" {
				values := append([]string{}, linkList(strings.NewReplacer("|", ",", ";", ",").Replace(value))...)
				if len(values) > 0 || field.target == "host-key-algorithms" {
					node[field.target] = values
				}
			}
		}
		idle := jsTrimSpace(pickQueryParam(query, "idle-timeout", "idle_timeout"))
		if isLinkDigitString(idle) {
			if value, ok := linkIntegerPrefix(url.Values{"idle": {idle}}, "idle"); ok {
				node["idle-timeout"] = value
			}
		}
	}
}

func simpleProxyHeaders(raw string) map[string]any {
	raw = jsTrimSpace(raw)
	if raw == "" {
		return nil
	}
	result := map[string]any{}
	if object, err := parseLinkJSON(raw); err == nil {
		for key, value := range object {
			if key = jsTrimSpace(key); key == "" {
				continue
			}
			switch value := value.(type) {
			case string:
				result[key] = value
			case float64:
				if value == 0 {
					value = 0
				}
				encoded, _ := json.Marshal(value)
				result[key] = string(encoded)
			case bool:
				result[key] = fmt.Sprint(value)
			}
		}
	} else {
		for part := range strings.SplitSeq(raw, "|") {
			key, value, found := strings.Cut(jsTrimSpace(part), ":")
			key = jsTrimSpace(key)
			if found && key != "" {
				result[key] = jsTrimSpace(value)
			}
		}
	}
	if len(result) == 0 {
		return nil
	}
	return result
}
