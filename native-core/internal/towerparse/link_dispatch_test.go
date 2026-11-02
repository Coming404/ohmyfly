package towerparse

import "testing"

func TestParseLinkDispatchesQUICSchemes(t *testing.T) {
	cases := []struct {
		input    string
		wantType string
	}{
		{"hysteria://hy.example.com:8443?auth=secret", "hysteria"},
		{"hy://hy.example.com:8443?auth=secret", "hysteria"},
		{"hysteria2://secret@hy2.example.com:8443", "hysteria2"},
		{"hy2://secret@hy2.example.com:8443", "hysteria2"},
		{"HY2://secret@hy2.example.com:8443", "hysteria2"},
		{"tuic://00000000-0000-0000-0000-000000000001:pass@tuic.example.com:443", "tuic"},
		{"anytls://secret@anytls.example.com:443", "anytls"},
		{"wireguard://private@wg.example.com:51820", "wireguard"},
		{"wg://private@wg.example.com", "wireguard"},
		{"WG://private@wg.example.com", "wireguard"},
		{"snell://psk@snell.example.com:443", "snell"},
	}
	for _, tc := range cases {
		t.Run(tc.input, func(t *testing.T) {
			node, _, handled, err := parseLink(tc.input)
			if !handled || err != nil {
				t.Fatalf("parseLink(%q) handled=%v err=%v", tc.input, handled, err)
			}
			if got := node["type"]; got != tc.wantType {
				t.Fatalf("type = %v, want %s", got, tc.wantType)
			}
		})
	}
}
