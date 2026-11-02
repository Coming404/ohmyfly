// Ported from SubBoost packages/core/src/parser/protocols/ss.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"net/url"
	"testing"
)

func TestSubBoostSSLinks(t *testing.T) {
	tests := []struct {
		name  string
		input string
		want  map[string]any
	}{
		// SubBoost packages/core/src/parser/protocols/ss.test.ts:9.
		{"legacy base64", "ss://" + subBoostBase64("aes-128-gcm:secret@ss.example.com:8388") + "#Full", map[string]any{"name": "Full", "server": "ss.example.com", "port": 8388, "cipher": "aes-128-gcm", "password": "secret", "udp": true}},
		// SubBoost packages/core/src/parser/protocols/ss.test.ts:10.
		{"SIP002 IPv6 plugin", "ss://aes-256-gcm:pass@[2001:db8::1]:8388?plugin=" + url.QueryEscape("v2ray-plugin;mode=websocket;mux=1;tls=0") + "&uot=1&tfo=yes#IPv6", map[string]any{"name": "IPv6", "server": "2001:db8::1", "plugin": "v2ray-plugin", "plugin-opts": map[string]any{"mode": "websocket", "mux": true, "tls": false}, "udp-over-tcp": true, "tfo": true}},
		// SubBoost packages/core/src/parser/protocols/ss.test.ts:55.
		{"v2ray plugin JSON", "ss://" + subBoostBase64("aes-128-gcm:secret") + "@ss-json.example.com:8388?v2ray-plugin=" + url.QueryEscape(subBoostBase64(`{"mode":"websocket","host":"cdn.example.com","mux":"0"}`)) + "#JSON", map[string]any{"name": "JSON", "plugin": "v2ray-plugin", "plugin-opts": map[string]any{"mode": "websocket", "host": "cdn.example.com", "mux": false}}},
		// SubBoost packages/core/src/parser/protocols/ss.test.ts:74.
		{"empty flags", "ss://aes-128-gcm:secret@bool.example.com:8388?uot=&tfo=#Bool", map[string]any{"name": "Bool", "udp-over-tcp": true, "tfo": true}},
		// SubBoost packages/core/src/parser/protocols/ss.test.ts:135.
		{"escaped plugin", "ss://" + subBoostBase64("aes-128-gcm:secret") + "@escaped.example.com:8388?plugin=" + url.QueryEscape(`obfs-local;obfs=tls;obfs-host=cdn\;edge.example.com;flag;empty=`) + "&tfo=on#Escaped", map[string]any{"name": "Escaped", "plugin": "obfs", "plugin-opts": map[string]any{"mode": "tls", "host": "cdn;edge.example.com"}, "tfo": true}},
		// SubBoost packages/core/src/parser/protocols/ss.test.ts:158.
		{"full IPv6 base64", "ss://" + subBoostBase64("aes-128-gcm:secret@[2001:db8::2]:8388") + "#FullIPv6", map[string]any{"name": "FullIPv6", "server": "2001:db8::2", "port": 8388}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := parseSSLink(tt.input)
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, got, tt.want)
		})
	}
}

// Ported from SubBoost packages/core/src/parser/protocols/ss.test.ts:84.
func TestSubBoostSSPluginNormalization(t *testing.T) {
	tests := []struct {
		name   string
		plugin string
		input  map[string]any
		want   string
		opts   map[string]any
	}{
		// SubBoost packages/core/src/parser/protocols/ss.test.ts:86.
		{"empty", "", nil, "", nil},
		// SubBoost packages/core/src/parser/protocols/ss.test.ts:90.
		{"obfs", "obfs", nil, "obfs", nil},
		// SubBoost packages/core/src/parser/protocols/ss.test.ts:94.
		{"obfs-local", "obfs-local", map[string]any{"obfs": "http", "obfs-host": "cdn.example.com"}, "obfs", map[string]any{"mode": "http", "host": "cdn.example.com"}},
		// SubBoost packages/core/src/parser/protocols/ss.test.ts:98.
		{"gost", "gost-plugin", map[string]any{"tls": "yes", "mux": 0, "extra": "keep"}, "gost-plugin", map[string]any{"tls": true, "mux": false, "extra": "keep"}},
		// SubBoost packages/core/src/parser/protocols/ss.test.ts:113.
		{"simple empty", "simple-obfs", map[string]any{"mode": "", "host": ""}, "obfs", nil},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, opts := normalizeSSPlugin(tt.plugin, tt.input)
			if got != tt.want || (tt.opts == nil && opts != nil) || (tt.opts != nil && len(opts) != len(tt.opts)) {
				t.Fatalf("normalizeSSPlugin=(%q,%#v), want (%q,%#v)", got, opts, tt.want, tt.opts)
			}
			if tt.opts != nil {
				assertMapSubset(t, opts, tt.opts)
			}
		})
	}
}
