// Ported from SubBoost packages/core/src/parser/protocols/snell.ts @4015347 (AGPL-3.0-only).
// Tests ported from protocols/ssr-snell.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"strings"
	"testing"
)

func TestSnellLinks(t *testing.T) {
	cases := []struct {
		name   string
		input  string
		want   map[string]any
		absent []string
	}{
		{
			name: "upstream URL credentials and optional fields",
			input: "snell://psk@snell.example.com:443?version=3&obfs=tls&obfs-host=cdn.example.com&obfs-uri=/obfs" +
				"&udp-relay=0&reuse=1&fast-open=yes&shadow-tls-version=2&shadow-tls-sni=sni.example.com" +
				"&shadow-tls-password=shadow#Snell",
			want: map[string]any{
				"name": "Snell", "type": "snell", "server": "snell.example.com", "port": 443, "psk": "psk", "version": 3,
				"obfs-opts": map[string]any{"mode": "tls", "host": "cdn.example.com", "path": "/obfs"},
				"udp":       false, "reuse": true, "tfo": true,
			},
			absent: []string{"shadow-tls-version", "shadow-tls-sni", "shadow-tls-password"},
		},
		{
			name:  "upstream query credentials",
			input: "snell://snell-query.example.com:443?psk=query-secret",
			want: map[string]any{
				"name": "snell-query.example.com:443", "psk": "query-secret", "server": "snell-query.example.com",
			},
		},
		{
			name: "upstream parser contract",
			input: "snell://psk@snell.example.com:443?version=3&obfs=tls&obfs-host=cdn.example.com" +
				"&obfs-uri=%2Ffront&udp-relay=true&fast-open=1#Snell",
			want: map[string]any{
				"version": 3, "obfs-opts": map[string]any{"mode": "tls", "host": "cdn.example.com", "path": "/front"},
				"udp": true, "tfo": true,
			},
		},
		{
			name:  "IPv6 default name and numeric port prefix",
			input: "snell://psk@[2001:db8::1]:443abc",
			want:  map[string]any{"name": "[2001:db8::1]:443", "server": "2001:db8::1", "port": 443},
		},
		{
			name:  "signed port last at and one credential decode",
			input: "snell://u@p:p%3A%2B%2520@snell.example.com:+12#%2520%2B+%E4%B8%AD",
			want:  map[string]any{"name": "%20+ 中", "psk": "u@p:p:+%20", "port": 12},
		},
		{
			name:  "malformed percent preserved",
			input: "snell://p%zz@snell.example.com#bad+%zz",
			want:  map[string]any{"psk": "p%zz", "name": "bad+%zz"},
		},
		{
			name:  "query aliases form semantics and no second credential decode",
			input: "snell://h?auth_str=++p%2520%zz++&version=%2B12abc&obfs=+HTTP+&obfs-host=+&obfs-uri=%252F",
			want: map[string]any{
				"psk": "p%20%zz", "version": 12, "obfs-opts": map[string]any{"mode": "http", "host": " ", "path": "%2F"},
			},
		},
		{
			name: "shadow TLS aliases normalized",
			input: "snell://psk@h?shadow-tls-version=2abc&shadow-tls-sni=sni.example.com&shadow-tls-password=shadow" +
				"&version=-3.5&udp-relay=on&reuse=off&fast-open=bad&tfo=yes",
			want: map[string]any{
				"version": -3, "udp": true, "reuse": false, "tfo": true,
				"obfs-opts": map[string]any{"mode": "shadow-tls", "version": 2, "host": "sni.example.com", "password": "shadow"},
			},
			absent: []string{"shadow-tls-version", "shadow-tls-sni", "shadow-tls-password"},
		},
		{
			name:  "invalid obfs does not suppress shadow TLS",
			input: "snell://psk@h?obfs=invalid&shadow-tls-version=bad&shadow-tls-password=+",
			want:  map[string]any{"obfs-opts": map[string]any{"mode": "shadow-tls", "password": " "}},
		},
		{
			name:   "invalid optional values omitted",
			input:  "snell://psk@h?version=bad&obfs=invalid&udp-relay=bad&reuse=&fast-open=bad&tfo=bad&shadow-tls-version=bad",
			absent: []string{"version", "obfs-opts", "udp", "reuse", "tfo"},
		},
		{
			name:  "false primary boolean wins and zero numeric prefix is allowed",
			input: "snell://psk@h?version=0x10&fast-open=NO&tfo=yes&udp-relay=YES&reuse=FALSE",
			want:  map[string]any{"version": 0, "tfo": false, "udp": true, "reuse": false},
		},
		{
			name:  "query PSK priority",
			input: "snell://h?psk=first&password=second&auth=third&auth-str=fourth",
			want:  map[string]any{"psk": "first"},
		},
		{
			name:  "password only userinfo retains colon",
			input: "snell://:secret@h?psk=ignored",
			want:  map[string]any{"psk": ":secret"},
		},
		{
			name:  "empty userinfo password drops colon",
			input: "snell://secret:@h?psk=ignored",
			want:  map[string]any{"psk": "secret"},
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			node, err := parseSnellLink(tc.input)
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, node, tc.want)
			assertMapAbsent(t, node, tc.absent...)
		})
	}
}

func TestSnellInvalidLinks(t *testing.T) {
	for _, tc := range []struct{ name, input, message string }{
		{"wrong scheme", "http://bad", "无效的 Snell 链接"},
		{"case sensitive scheme", "Snell://psk@h", "无效的 Snell 链接"},
		{"missing PSK", "snell://@snell.example.com:443", "Snell 配置缺少必要字段"},
		{"missing server", "snell://psk@:443", "Snell 配置缺少必要字段"},
		{"whitespace PSK blocks alias", "snell://h?psk=+&auth=secret", "Snell 配置缺少必要字段"},
		{"empty userinfo", "snell://:@h", "Snell 配置缺少必要字段"},
		{"port too large", "snell://psk@h:70000", "无效的端口号"},
		{"hex port prefix is zero", "snell://psk@h:0x10", "无效的端口号"},
		{"zero port", "snell://psk@h:0", "无效的端口号"},
		{"negative port", "snell://psk@h:-12", "无效的端口号"},
		{"malformed port", "snell://psk@h:%zz", "无效的端口号"},
		{"malformed IPv6", "snell://psk@[::1", "invalid URL"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := parseSnellLink(tc.input); err == nil || !strings.Contains(err.Error(), tc.message) {
				t.Fatalf("error = %v, want %q", err, tc.message)
			}
		})
	}
}
