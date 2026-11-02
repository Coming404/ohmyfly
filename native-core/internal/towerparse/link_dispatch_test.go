package towerparse

import (
	"strings"
	"testing"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

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

func TestParseLinkDispatchesSimpleProxySchemes(t *testing.T) {
	cases := []struct {
		input    string
		wantType string
	}{
		{"socks5://u:p@socks.example.com:1080", "socks5"},
		{"SOCKS5://u:p@socks.example.com:1080", "socks5"},
		{"socks://socks.example.com:1080", "socks5"},
		{"socks4://socks4.example.com:1080", "socks4"},
		{"socks5+tls://socks.example.com:443", "socks5"},
		{"ssh://root:p@ssh.example.com:22", "ssh"},
		{"tg://socks?server=tg.example.com&port=1080", "socks5"},
		{"TG://http?server=tg.example.com&port=8080", "http"},
		{"https://t.me/socks?server=tg.example.com&port=1080", "socks5"},
		{"https://t.me/https?server=tg.example.com&port=443", "https"},
		{"http://t.me/http?server=tg.example.com&port=8080", "http"},
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

func TestParseLinkLeavesPlainHTTPUnhandled(t *testing.T) {
	for _, input := range []string{
		"http://proxy.example.com:8080",
		"https://proxy.example.com",
		"https://example.com/sub?token=abc",
		"HTTPS://example.com/sub",
		"https://t.me/mtproto?server=h&port=443",
		"https://t.me/channel",
	} {
		t.Run(input, func(t *testing.T) {
			node, _, handled, err := parseLink(input)
			if handled || err != nil || node != nil {
				t.Fatalf("parseLink(%q) = %v handled=%v err=%v, want unhandled", input, node, handled, err)
			}
		})
	}
}

func TestParseEmitsSimpleProxyLinks(t *testing.T) {
	input := "socks5://u:p@socks.example.com:1080#S\nssh://root:p@ssh.example.com:22#H\ntg://http?server=tg.example.com&port=8080&remark=T\nhttps://example.com/sub?token=abc\n"
	var names []string
	stats, err := Parse(strings.NewReader(input), func(value flight.Flight) error {
		names = append(names, value.Name)
		return nil
	})
	if err != nil {
		t.Fatalf("parse failed: %v", err)
	}
	if stats.Parsed != 3 || stats.Skipped != 1 || strings.Join(names, ",") != "S,H,T" {
		t.Fatalf("unexpected stats %+v names %v", stats, names)
	}
}
