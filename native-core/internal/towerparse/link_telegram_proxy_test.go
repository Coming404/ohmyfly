// Ported from SubBoost packages/core/src/parser/protocols/simple-proxy.ts and simple-proxy.test.ts @4015347 (AGPL-3.0-only).
// Telegram regression cases ported from test/regression/core/parser-{matrix,protocol-matrix,link-edge-cases,edge-cases,and-template-edge-cases,and-template-regressions}.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import "testing"

func TestTelegramProxyLinks(t *testing.T) {
	testSimpleProxyLinks(t, parseTelegramProxyLink, []simpleProxyTestCase{
		{"TG SOCKS credentials", "tg://socks?server=tg.example.com&port=1080&user=u&pass=p&remark=TG", map[string]any{"name": "TG", "type": "socks5", "server": "tg.example.com", "port": 1080, "username": "u", "password": "p", "udp": true}, []string{"tls"}, ""},
		{"t.me HTTPS", "https://t.me/https?server=tg-http.example.com&port=443", map[string]any{"name": "HTTPS-tg-http.example.com:443", "type": "https", "server": "tg-http.example.com", "port": 443, "tls": true}, nil, ""},
		{"TG HTTP default name", "tg://http?server=default-name.example.com&port=8080", map[string]any{"name": "HTTP-default-name.example.com:8080", "type": "http", "server": "default-name.example.com", "port": 8080, "tls": false}, nil, ""},
		{"TG HTTPS remarks alias", "tg://https?server=tg-secure.example.com&port=443&remarks=SecureTG", map[string]any{"name": "SecureTG", "type": "https", "tls": true}, nil, ""},
		{"t.me nested SOCKS path", "https://t.me/socks/proxy?server=tg-path.example.com&port=1080", map[string]any{"name": "SOCKS-tg-path.example.com:1080", "type": "socks5", "server": "tg-path.example.com"}, nil, ""},
		{"regression t.me HTTP credentials and remarks", "https://t.me/http?server=tg.example.com&port=443&user=u&pass=p&remarks=TG%20HTTP", map[string]any{"name": "TG HTTP", "type": "http", "port": 443, "username": "u", "password": "p", "tls": false}, nil, ""},
		{"regression TG secure remark", "tg://https?server=tg.example.com&port=443&remark=Secure", map[string]any{"name": "Secure", "type": "https", "tls": true}, nil, ""},
		{"regression t.me secure remark", "https://t.me/https?server=tg.example.com&port=443&remark=Telegram", map[string]any{"name": "Telegram", "type": "https", "tls": true}, nil, ""},
		{"regression TG HTTP remarks", "tg://http?server=http-tg.example.com&port=8080&remarks=HTTP%20TG", map[string]any{"type": "http", "name": "HTTP TG"}, nil, ""},
		{"query double decoding first values and numeric prefix", "tg://socks?server=+h+&server=ignored&port=%2B1080tail&user=u%2520&pass=p%252B&remark=A%2520+B&remarks=ignored", map[string]any{"server": "h", "port": 1080, "username": "u ", "password": "p+", "name": "A  B"}, nil, ""},
		{"IPv6 default name", "tg://http?server=2001%3Adb8%3A%3A1&port=8080", map[string]any{"name": "HTTP-2001:db8::1:8080", "server": "2001:db8::1"}, nil, ""},
		{"case insensitive kind and leading path slashes", "http://T.ME///SoCkS/proxy?server=h&port=1080", map[string]any{"type": "socks5", "server": "h"}, nil, ""},
		{"case insensitive TG authority", "TG://SOCKS?server=h&port=1080", map[string]any{"type": "socks5", "server": "h"}, nil, ""},
		{"query options and hash are ignored", "tg://socks?server=h&port=1080&udp=0&sni=ignored&allow-insecure=yes#Ignored", map[string]any{"name": "SOCKS-h:1080", "udp": true}, []string{"sni", "skip-cert-verify", "tls"}, ""},
		{"malformed query escapes are preserved", "tg://http?server=h&port=8080&user=u%zz&remark=bad+%zz", map[string]any{"username": "u%zz", "name": "bad %zz"}, nil, ""},
		{"maximum port", "tg://http?server=h&port=65535", map[string]any{"port": 65535}, nil, ""},
		{"bad kind", "tg://bad?server=x&port=1", nil, nil, "无效的 Telegram 代理链接"},
		{"wrong host", "https://example.com/socks?server=x&port=1080", nil, nil, "无效的 Telegram 代理链接"},
		{"empty path", "https://t.me/?server=x&port=1080", nil, nil, "无效的 Telegram 代理链接"},
		{"missing server", "tg://socks?port=1080", nil, nil, "缺少服务器地址"},
		{"large port", "tg://socks?server=x&port=70000", nil, nil, "无效的端口号"},
		{"zero port", "tg://socks?server=tg.example.com&port=0", nil, nil, "无效的端口号"},
		{"bad port", "tg://socks?server=tg.example.com&port=bad", nil, nil, "无效的端口号"},
		{"missing port", "https://t.me/http?server=tg.example.com&port=", nil, nil, "无效的端口号"},
		{"negative port", "tg://socks?server=h&port=-1", nil, nil, "无效的端口号"},
		{"unsupported MTProto", "tg://mtproto?server=tg.example.com&port=443", nil, nil, "无效的 Telegram 代理链接"},
		{"malformed URL", "https://[t.me/socks?server=h&port=1080", nil, nil, "无效的 Telegram 代理链接"},
	})
}

func TestTelegramHTTPProxyDetection(t *testing.T) {
	for _, tc := range []struct {
		input string
		want  bool
	}{
		{"https://t.me/socks?server=h&port=1080", true},
		{"http://t.me/http", true},
		{"https://T.ME///HTTPS/proxy", true},
		{"https://example.com/socks", false},
		{"https://t.me/", false},
		{"https://t.me/mtproto", false},
		{"https://t.me/%73ocks", false},
		{"tg://socks?server=h&port=1080", false},
		{"https://[t.me/socks", false},
	} {
		t.Run(tc.input, func(t *testing.T) {
			if got := isTelegramHTTPProxyLink(tc.input); got != tc.want {
				t.Fatalf("isTelegramHTTPProxyLink = %v, want %v", got, tc.want)
			}
		})
	}
}
