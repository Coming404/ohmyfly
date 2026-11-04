// TUIC、WireGuard 与 SubBoost 4015347 的对齐与有意偏离。
package towerparse

import "testing"

// 默认名与上游一致：TUIC 为 "TUIC-host:port"，WireGuard 为 "WireGuard host:port"，IPv6 都不加方括号。
func TestSubBoostTUICWireGuardDefaultNames(t *testing.T) {
	cases := []struct {
		name, input, want string
		parse             func(string) (map[string]any, error)
	}{
		{"tuic", "tuic://tok@t.example.com", "TUIC-t.example.com:443", parseTUICLink},
		{"tuic empty fragment", "tuic://tok@[2001:db8::1]:8443#", "TUIC-2001:db8::1:8443", parseTUICLink},
		{"wireguard", "wg://key@w.example.com", "WireGuard w.example.com:51820", parseWireGuardLink},
		{"wireguard IPv6", "wireguard://key@[2001:db8::1]:51821", "WireGuard 2001:db8::1:51821", parseWireGuardLink},
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

// mihomo 的 WireGuardOption 里 dns、allowed-ips 是 []string，tfo 等是 bool；上游原样写字符串，节点加载时报错。
func TestWireGuardPassthroughTypedForMihomo(t *testing.T) {
	cases := []struct {
		name, query string
		want        map[string]any
		absent      []string
	}{
		{"dns list", "dns=1.1.1.1,%208.8.8.8,", map[string]any{"dns": []string{"1.1.1.1", "8.8.8.8"}}, nil},
		{"allowed ips underscore", "allowed_ips=0.0.0.0/0,::/0", map[string]any{"allowed-ips": []string{"0.0.0.0/0", "::/0"}}, nil},
		{"bools", "remote-dns-resolve=true&tfo=0&mptcp=yes", map[string]any{"remote-dns-resolve": true, "tfo": false, "mptcp": true}, nil},
		{"invalid bool dropped", "remote-dns-resolve=maybe", nil, []string{"remote-dns-resolve"}},
		{"empty list dropped", "dns=,%20", nil, []string{"dns"}},
		{"other kept as string", "workers=2", map[string]any{"workers": "2"}, nil},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			node, err := parseWireGuardLink("wg://key@w.example.com?" + tc.query)
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, node, tc.want)
			assertMapAbsent(t, node, tc.absent...)
		})
	}
}
