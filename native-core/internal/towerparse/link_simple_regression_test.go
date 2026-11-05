// Snell、SOCKS、HTTP、SSH、Telegram 代理链接与 SubBoost 4015347 的默认名对齐。
package towerparse

import "testing"

// 默认名与上游一致：Snell 为 "Snell host:port"，其余为类型大写加 "-host:port"，
// socks4、socks5 都用 SOCKS-，IPv6 都不加方括号。
func TestSubBoostSimpleProxyDefaultNames(t *testing.T) {
	cases := []struct {
		name, input, want string
		parse             func(string) (map[string]any, error)
	}{
		{"snell", "snell://psk@s.example.com", "Snell s.example.com:443", parseSnellLink},
		{"snell IPv6", "snell://psk@[2001:db8::1]:8443#", "Snell 2001:db8::1:8443", parseSnellLink},
		{"socks5", "socks5://s.example.com", "SOCKS-s.example.com:1080", parseSocksLink},
		{"socks4", "socks4://s.example.com:1081", "SOCKS-s.example.com:1081", parseSocksLink},
		{"socks5 tls", "socks5+tls://[2001:db8::1]", "SOCKS-2001:db8::1:443", parseSocksLink},
		{"socks colon format", "socks5://s.example.com:1080:u:p", "SOCKS-s.example.com:1080", parseSocksLink},
		{"http", "http://h.example.com", "HTTP-h.example.com:80", parseHTTPProxyLink},
		{"https", "https://h.example.com", "HTTPS-h.example.com:443", parseHTTPProxyLink},
		{"ssh", "ssh://u@[2001:db8::1]", "SSH-2001:db8::1:22", parseSSHLink},
		{"tg socks", "tg://socks?server=1.2.3.4&port=1080", "SOCKS-1.2.3.4:1080", parseTelegramProxyLink},
		{"t.me https", "https://t.me/https?server=1.2.3.4&port=443", "HTTPS-1.2.3.4:443", parseTelegramProxyLink},
		// 有意偏离：上游会把用户显式写的 SOCKS5-host:port 也改成 SOCKS-，本地保留。
		{"explicit SOCKS5 name kept", "socks5://s.example.com:1080#SOCKS5-s.example.com:1080", "SOCKS5-s.example.com:1080", parseSocksLink},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			node, err := tc.parse(tc.input)
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, node, map[string]any{"name": tc.want})
		})
	}
}
