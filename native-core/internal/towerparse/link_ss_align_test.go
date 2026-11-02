package towerparse

import (
	"encoding/base64"
	"testing"
)

func TestSSUpstreamAlignment(t *testing.T) {
	b64 := func(value string) string { return base64.StdEncoding.EncodeToString([]byte(value)) }
	cases := []struct {
		name     string
		link     string
		wantErr  bool
		server   string
		port     int
		cipher   string
		password string
	}{
		{name: "empty server", link: "ss://aes-256-gcm:pw@:443", server: "", port: 443, cipher: "aes-256-gcm", password: "pw"},
		{name: "empty cipher", link: "ss://:pw@h.example.com:443", server: "h.example.com", port: 443, cipher: "", password: "pw"},
		{name: "ipv6 without port", link: "ss://aes-256-gcm:pw@[::1]", server: "[:", port: 1, cipher: "aes-256-gcm", password: "pw"},
		{name: "ipv6 with port", link: "ss://aes-256-gcm:pw@[::1]:443", server: "::1", port: 443, cipher: "aes-256-gcm", password: "pw"},
		{name: "ipv6 plugin trailing slash", link: "ss://aes-256-gcm:pw@[::1]:443/?plugin=obfs-local%3Bobfs%3Dhttp", server: "::1", port: 443, cipher: "aes-256-gcm", password: "pw"},
		{name: "trailing slash", link: "ss://aes-256-gcm:pw@h.example.com:443/", server: "h.example.com", port: 443, cipher: "aes-256-gcm", password: "pw"},
		{name: "port trailing junk", link: "ss://aes-256-gcm:pw@h.example.com:443abc", server: "h.example.com", port: 443, cipher: "aes-256-gcm", password: "pw"},
		{name: "no port colon", link: "ss://aes-256-gcm:pw@h.example.com", wantErr: true},
		{name: "empty port", link: "ss://aes-256-gcm:pw@h.example.com:", wantErr: true},
		{name: "port zero", link: "ss://aes-256-gcm:pw@h.example.com:0", wantErr: true},
		{name: "port too large", link: "ss://aes-256-gcm:pw@h.example.com:70000", wantErr: true},
		{name: "port not number", link: "ss://aes-256-gcm:pw@h.example.com:abc", wantErr: true},
		{name: "base64 userinfo", link: "ss://" + b64("aes-256-gcm:pw") + "@h.example.com:443", server: "h.example.com", port: 443, cipher: "aes-256-gcm", password: "pw"},
		{name: "base64 userinfo without colon", link: "ss://" + b64("nocolon") + "@h.example.com:443", wantErr: true},
		{name: "payload empty server", link: "ss://" + b64("aes-256-gcm:pw@:443"), server: "", port: 443, cipher: "aes-256-gcm", password: "pw"},
		{name: "payload ipv6 without port", link: "ss://" + b64("aes-256-gcm:pw@[::1]"), server: "[:", port: 1, cipher: "aes-256-gcm", password: "pw"},
		{name: "payload empty cipher", link: "ss://" + b64(":pw@h.example.com:443"), server: "h.example.com", port: 443, cipher: "", password: "pw"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			node, err := parseSSLink(tc.link)
			if tc.wantErr {
				if err == nil {
					t.Fatalf("want error, got %v", node)
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if node["server"] != tc.server || node["port"] != tc.port || node["cipher"] != tc.cipher || node["password"] != tc.password {
				t.Fatalf("got server=%q port=%v cipher=%q password=%q", node["server"], node["port"], node["cipher"], node["password"])
			}
			if name, _ := node["name"].(string); name == "" {
				t.Fatalf("empty default name")
			}
		})
	}
}
