// Ported from SubBoost packages/core/src/parser/protocols/wireguard.ts @4015347 (AGPL-3.0-only).
// Tests ported from protocols/more-protocols.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"strings"
	"testing"
)

func TestWireGuardLinks(t *testing.T) {
	cases := []struct {
		name   string
		input  string
		want   map[string]any
		absent []string
	}{
		{
			name: "upstream peer aliases and metadata",
			input: "wg://private-key@wg.example.com:51820?publicKey=public&presharedkey=psk" +
				"&address=10.0.0.2/32,[fd00::2]/128&reserved=1,2,3&mtu=1280&udp=0&flag=ignored&keepalive=25#WG",
			want: map[string]any{
				"name": "WG", "type": "wireguard", "server": "wg.example.com", "port": 51820,
				"private-key": "private-key", "public-key": "public", "pre-shared-key": "psk",
				"ip": "10.0.0.2", "ipv6": "fd00::2", "reserved": []int{1, 2, 3},
				"mtu": 1280, "udp": false, "persistent-keepalive": 25,
			},
			absent: []string{"flag", "keepalive", "address"},
		},
		{
			name: "upstream query credentials and invalid optional values",
			input: "wireguard://wg-query.example.com?private_key=private&publickey=public&ip=10.0.0.2/32" +
				"&reserved=1,2&mtu=bad&udp=maybe&extra=value",
			want: map[string]any{
				"name": "wg-query.example.com:51820", "private-key": "private", "public-key": "public",
				"ip": "10.0.0.2", "udp": true, "extra": "value",
			},
			absent: []string{"reserved", "mtu"},
		},
		{
			name: "upstream address contract",
			input: "wg://private-key@wg.example.com:51820?public-key=public-key&pre-shared-key=pre-shared" +
				"&reserved=1,2,3&address=10.0.0.2%2F32,%5Bfd00%3A%3A2%5D%2F128&mtu=1420&udp=false#WG",
			want: map[string]any{
				"name": "WG", "private-key": "private-key", "public-key": "public-key", "pre-shared-key": "pre-shared",
				"reserved": []int{1, 2, 3}, "ip": "10.0.0.2", "ipv6": "fd00::2", "mtu": 1420, "udp": false,
			},
		},
		{
			name:  "IPv6 default name and credential decoded once",
			input: "wireguard://p%3A%2B%2520:ignored@[2001:db8::1]",
			want: map[string]any{
				"name": "[2001:db8::1]:51820", "server": "2001:db8::1", "port": 51820, "private-key": "p:+%20",
			},
		},
		{
			name:  "last at malformed escapes and numeric port prefix",
			input: "wg://u@p%zz@wg.example.com:443abc?extra=%zz#bad+%zz",
			want: map[string]any{
				"private-key": "u@p%zz", "name": "bad+%zz", "port": 443, "extra": "%zz",
			},
		},
		{
			name:  "signed port and form decoded name",
			input: "wireguard://private@wg.example.com:+12#%2520%2B+%E4%B8%AD",
			want:  map[string]any{"name": "%20+ 中", "port": 12},
		},
		{
			name:   "URLSearchParams strips its leading question mark",
			input:  "wg://h??private_key=private&publicKey=public",
			want:   map[string]any{"private-key": "private", "public-key": "public"},
			absent: []string{"?private-key"},
		},
		{
			name: "query order and alias override precedence",
			input: "wg://authority@h?private-key=ignored&privateKey=middle&privatekey=final" +
				"&public-key=first&publicKey=middle&public-key=ignored&peer_public_key=last" +
				"&extra=first&extra=second&name=ignored&server=ignored&port=0&udp=on&udp=off",
			want: map[string]any{
				"private-key": "final", "public-key": "last", "extra": "first", "name": "h:51820",
				"server": "h", "port": 51820, "udp": false,
			},
			absent: []string{"peer-public-key", "privateKey", "publicKey"},
		},
		{
			name: "query values decoded twice keys once",
			input: "wg://h?private_key=%252B&extra=%252B&literal=+&%20other_key%20=%2520" +
				"&publicKey=pub%252B&pre_shared_key=psk%2525&mtu=%252B12ms&flag=x&empty=",
			want: map[string]any{
				"private-key": "%2B", "extra": "+", "literal": " ", "other-key": " ",
				"public-key": "pub+", "pre-shared-key": "psk%", "mtu": 12,
			},
			absent: []string{"flag", "empty"},
		},
		{
			name:   "reserved base64 and canonical keepalive wins",
			input:  "wg://private@h?reserved=AQID&keepalive=12&persistentKeepalive=13&persistent_keepalive=0",
			want:   map[string]any{"reserved": []int{1, 2, 3}, "persistent-keepalive": 0},
			absent: []string{"keepalive", "persistentKeepalive", "persistent_keepalive"},
		},
		{
			name: "last valid reserved address and mtu win",
			input: "wg://private@h?reserved=1,2,3&reserved=4,5,6&reserved=bad&mtu=-3.5&mtu=bad" +
				"&address=10.0.0.1/32,10.0.0.2/24,[fd00::1]/128,[fd00::2]/64&persistentKeepalive=00025",
			want: map[string]any{
				"reserved": []int{4, 5, 6}, "mtu": -3, "ip": "10.0.0.2", "ipv6": "fd00::2", "persistent-keepalive": 25,
			},
			absent: []string{"persistentKeepalive"},
		},
		{
			name:  "no address validation and optional empty canonical keepalive",
			input: "wg://private@h?address=not-an-ip/abc&persistent-keepalive=&keepalive=12&mtu=0x10",
			want:  map[string]any{"ip": "not-an-ip/abc", "persistent-keepalive": 12, "mtu": 0},
		},
		{
			name:   "JavaScript inherited fields are not imported",
			input:  "wg://private@h?constructor=x&toString=x&__proto__=x&hasOwnProperty=x",
			absent: []string{"constructor", "toString", "__proto__", "hasOwnProperty"},
			want:   map[string]any{"--proto--": "x"},
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			node, err := parseWireGuardLink(tc.input)
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, node, tc.want)
			assertMapAbsent(t, node, tc.absent...)
		})
	}
}

func TestWireGuardInvalidLinks(t *testing.T) {
	for _, tc := range []struct{ name, input, message string }{
		{"wrong scheme", "http://bad", "无效的 WireGuard 链接"},
		{"case sensitive scheme", "WG://private@h", "无效的 WireGuard 链接"},
		{"missing private key", "wireguard://@wg.example.com:51820", "WireGuard 配置缺少必要字段"},
		{"password is not a private key", "wg://:password@h", "WireGuard 配置缺少必要字段"},
		{"late lowercase alias cannot supply required key", "wg://h?privatekey=secret", "WireGuard 配置缺少必要字段"},
		{"missing server", "wg://private@:51820", "WireGuard 配置缺少必要字段"},
		{"port too large", "wireguard://private@h:70000", "无效的端口号"},
		{"hex port prefix is zero", "wg://private@h:0x10", "无效的端口号"},
		{"zero port", "wg://private@h:0", "无效的端口号"},
		{"negative port", "wg://private@h:-12", "无效的端口号"},
		{"malformed port", "wg://private@h:%zz", "无效的端口号"},
		{"malformed IPv6", "wg://private@[::1]tail", "invalid URL"},
		{"keepalive out of range", "wg://private@h?keepalive=70000", "persistent-keepalive 无效"},
		{"keepalive negative", "wg://private@h?keepalive=-1", "persistent-keepalive 无效"},
		{"keepalive sign", "wg://private@h?keepalive=%2B1", "persistent-keepalive 无效"},
		{"keepalive not integer", "wg://private@h?keepalive=1.5", "persistent-keepalive 无效"},
		{"keepalive not numeric prefix", "wg://private@h?keepalive=12ms", "persistent-keepalive 无效"},
		{"invalid canonical blocks alias", "wg://private@h?persistent-keepalive=bad&keepalive=12", "persistent-keepalive 无效"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := parseWireGuardLink(tc.input); err == nil || !strings.Contains(err.Error(), tc.message) {
				t.Fatalf("error = %v, want %q", err, tc.message)
			}
		})
	}
}

func TestWireGuardReserved(t *testing.T) {
	for _, tc := range []struct {
		name, value string
		want        []int
	}{
		{"decimal bytes", "0,255,001", []int{0, 255, 1}},
		{"trimmed bytes", "+1+,2,3+", []int{1, 2, 3}},
		{"standard base64", "AP8B", []int{0, 255, 1}},
		{"padded base64 is invalid", "AQI%3D", nil},
		{"URL safe base64 is invalid", "____", nil},
		{"too few bytes", "1,2", nil},
		{"too many bytes", "1,2,3,4", nil},
		{"byte out of range", "1,2,256", nil},
		{"negative byte", "1,2,-1", nil},
		{"signed byte", "1,2,%2B3", nil},
		{"decimal prefix invalid", "1,2,3x", nil},
	} {
		t.Run(tc.name, func(t *testing.T) {
			node, err := parseWireGuardLink("wg://private@h?reserved=" + tc.value)
			if err != nil {
				t.Fatal(err)
			}
			if tc.want == nil {
				assertMapAbsent(t, node, "reserved")
				return
			}
			assertMapSubset(t, node, map[string]any{"reserved": tc.want})
		})
	}
}
