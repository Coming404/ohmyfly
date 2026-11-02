// Ported from SubBoost packages/core/src/parser/protocols/simple-proxy.ts and link-parsers.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"fmt"
	"net/url"
	"strings"
)

func isTelegramHTTPProxyLink(raw string) bool {
	parsed, err := url.Parse(raw)
	return err == nil && strings.EqualFold(parsed.Hostname(), "t.me") && telegramProxyKind(parsed) != ""
}

func telegramProxyKind(parsed *url.URL) string {
	kind := ""
	if strings.EqualFold(parsed.Scheme, "tg") {
		kind = strings.ToLower(parsed.Hostname())
	} else if strings.EqualFold(parsed.Hostname(), "t.me") {
		kind = strings.ToLower(strings.Split(strings.TrimLeft(parsed.EscapedPath(), "/"), "/")[0])
	}
	switch kind {
	case "socks", "http", "https":
		return kind
	default:
		return ""
	}
}

func parseTelegramProxyLink(raw string) (map[string]any, error) {
	parsed, err := url.Parse(raw)
	if err != nil || parsed.Scheme == "" || parsed.Host == "" {
		return nil, fmt.Errorf("无效的 Telegram 代理链接")
	}
	kind := telegramProxyKind(parsed)
	if kind == "" {
		return nil, fmt.Errorf("无效的 Telegram 代理链接")
	}
	query := linkSearchParams(parsed.RawQuery)
	server := jsTrimSpace(query.Get("server"))
	if server == "" {
		return nil, fmt.Errorf("缺少服务器地址")
	}
	port, err := linkPort(jsTrimSpace(query.Get("port")), 0)
	if err != nil || port == 0 {
		return nil, fmt.Errorf("无效的端口号")
	}
	name := linkName(pickQueryParam(query, "remark", "remarks"), linkDefaultName(server, port))
	if kind == "socks" {
		kind = "socks5"
	}
	return buildSimpleProxyNode(kind, name, server, port,
		linkDecodeComponent(query.Get("user")), linkDecodeComponent(query.Get("pass")), false), nil
}
