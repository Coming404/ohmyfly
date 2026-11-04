package towerparse

import (
	"encoding/json"
	"fmt"
	"strings"
	"testing"
)

// Edge cases aligned with SubBoost (option B): lenient parseInt-style ports,
// lenient percent-decoding, last-@ userinfo split and neutral authority split.
func TestLinkUpstreamAlignmentEdgeCases(t *testing.T) {
	const uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"
	type parser func(string) (map[string]any, error)
	vmess := func(raw string) (map[string]any, error) {
		node, _, err := parseVMessLink(raw)
		return node, err
	}
	tests := []struct {
		name     string
		parse    parser
		input    string
		want     map[string]any
		wantErr  bool
		contains string
	}{
		{"trojan trailing garbage port", parseTrojanLink, "trojan://pw@h.example.com:443abc", map[string]any{"port": 443, "server": "h.example.com"}, false, ""},
		{"trojan plus sign port", parseTrojanLink, "trojan://pw@h.example.com:+12", map[string]any{"port": 12}, false, ""},
		{"trojan hex port", parseTrojanLink, "trojan://pw@h.example.com:0x10", nil, true, ""},
		{"trojan out of range port", parseTrojanLink, "trojan://pw@h.example.com:70000", nil, true, ""},
		{"trojan huge port", parseTrojanLink, "trojan://pw@h.example.com:99999999999", nil, true, ""},
		{"trojan ipv6", parseTrojanLink, "trojan://pw@[2001:db8::1]:8443", map[string]any{"server": "2001:db8::1", "port": 8443}, false, ""},
		{"trojan unclosed ipv6", parseTrojanLink, "trojan://pw@[2001:db8::1:8443", nil, true, ""},
		{"trojan ipv6 trailing junk", parseTrojanLink, "trojan://pw@[::1]x", nil, true, ""},
		{"trojan multi colon host", parseTrojanLink, "trojan://pw@a:b:443", map[string]any{"server": "a:b:443", "port": 443}, false, ""},
		{"trojan last at splits userinfo", parseTrojanLink, "trojan://u@x@h.example.com:443", map[string]any{"password": "u@x", "server": "h.example.com"}, false, ""},
		{"trojan name decoded once", parseTrojanLink, "trojan://pw@h.example.com:443#%2520a", map[string]any{"name": "%20a"}, false, ""},
		{"trojan invalid escape kept", parseTrojanLink, "trojan://pw@h.example.com:443?sni=%zz&type=ws&path=%zz", map[string]any{"sni": "%zz", "network": "ws"}, false, `"path":"%zz"`},
		{"trojan empty host", parseTrojanLink, "trojan://pw@:443", nil, true, ""},
		{"trojan fp chrome", parseTrojanLink, "trojan://pw@h.example.com:443?fp=chrome", map[string]any{"client-fingerprint": "chrome"}, false, ""},
		{"vless trailing garbage port", parseVLESSLink, "vless://" + uuid + "@h.example.com:443abc", map[string]any{"port": 443}, false, ""},
		{"vless plus sign port", parseVLESSLink, "vless://" + uuid + "@h.example.com:+12", map[string]any{"port": 12}, false, ""},
		{"vless hex port", parseVLESSLink, "vless://" + uuid + "@h.example.com:0x10", nil, true, ""},
		{"vless ipv6", parseVLESSLink, "vless://" + uuid + "@[2001:db8::1]:8443", map[string]any{"server": "2001:db8::1", "port": 8443}, false, ""},
		{"vless name decoded once", parseVLESSLink, "vless://" + uuid + "@h.example.com:443#%2520a", map[string]any{"name": "%20a"}, false, ""},
		{"vless invalid escape kept", parseVLESSLink, "vless://" + uuid + "@h.example.com:443?sni=%zz", map[string]any{"servername": "%zz"}, false, ""},
		{"vmess invalid path escape kept", vmess, "vmess://ws+tls:" + uuid + "-0@h.example.com:443/?path=%zz#V", nil, false, `"path":"%zz"`},
		{"hysteria trailing garbage port", parseHysteriaLink, "hysteria://h.example.com:443abc?auth=x", map[string]any{"port": 443}, false, ""},
		{"hysteria plus sign port", parseHysteriaLink, "hysteria://h.example.com:+12?auth=x", map[string]any{"port": 12}, false, ""},
		{"hysteria hex port", parseHysteriaLink, "hysteria://h.example.com:0x10?auth=x", nil, true, ""},
		{"hysteria invalid escape kept", parseHysteriaLink, "hysteria://h.example.com:443?auth=%zz", map[string]any{"auth-str": "%zz"}, false, ""},
		{"tuic trailing garbage port", parseTUICLink, "tuic://" + uuid + ":pw@h.example.com:443abc", map[string]any{"port": 443}, false, ""},
		{"tuic plus sign port", parseTUICLink, "tuic://" + uuid + ":pw@h.example.com:+12", map[string]any{"port": 12}, false, ""},
		{"tuic hex port", parseTUICLink, "tuic://" + uuid + ":pw@h.example.com:0x10", nil, true, ""},
		{"tuic invalid escape kept", parseTUICLink, "tuic://" + uuid + ":p%zz@h.example.com:443", map[string]any{"password": "p%zz"}, false, ""},
		{"hy2 non numeric authority becomes ports", parseHysteria2Link, "hy2://s@h.example.com:443abc", map[string]any{"port": 443, "ports": "443abc"}, false, ""},
		{"hy2 plus sign authority becomes ports", parseHysteria2Link, "hy2://s@h.example.com:+12", map[string]any{"port": 443, "ports": "+12"}, false, ""},
		{"hy2 out of range port", parseHysteria2Link, "hy2://s@h.example.com:99999", nil, true, ""},
		{"hy2 fp wins over pin", parseHysteria2Link, "hy2://s@h.example.com:443?fp=chrome&pinSHA256=AB", map[string]any{"fingerprint": "chrome"}, false, ""},
		{"hy2 invalid escape kept", parseHysteria2Link, "hy2://s%zz@h.example.com:443", map[string]any{"password": "s%zz"}, false, ""},
		{"ss invalid escape and garbage port", parseSSLink, "ss://aes-256-gcm:p%zz@h.example.com:443abc", map[string]any{"password": "p%zz", "port": 443}, false, ""},
		{"ss plus sign port", parseSSLink, "ss://aes-256-gcm:pw@h.example.com:+12", map[string]any{"port": 12}, false, ""},
		{"ss hex port", parseSSLink, "ss://aes-256-gcm:pw@h.example.com:0x10", nil, true, ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := tt.parse(tt.input)
			if tt.wantErr {
				if err == nil {
					t.Fatalf("expected error, got %+v", got)
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			for key, want := range tt.want {
				if fmt.Sprint(got[key]) != fmt.Sprint(want) {
					t.Errorf("%s = %#v, want %#v", key, got[key], want)
				}
			}
			if tt.contains != "" {
				encoded, _ := json.Marshal(got)
				if !strings.Contains(string(encoded), tt.contains) {
					t.Errorf("output %s does not contain %s", encoded, tt.contains)
				}
			}
		})
	}
}
