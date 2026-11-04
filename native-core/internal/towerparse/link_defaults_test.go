package towerparse

import (
	"encoding/base64"
	"strings"
	"testing"
)

const testUUID = "11111111-1111-1111-1111-111111111111"

func TestLinkCertificateFingerprint(t *testing.T) {
	hex := strings.Repeat("ab", 32)
	colon := strings.TrimSuffix(strings.Repeat("AB:", 32), ":")
	cases := []struct {
		name   string
		values []string
		want   string
	}{
		{"plain", []string{hex}, hex},
		{"colon upper", []string{colon}, hex},
		{"dash", []string{strings.ReplaceAll(colon, ":", "-")}, hex},
		{"short", []string{"abcd"}, ""},
		{"non hex", []string{strings.Repeat("zz", 32)}, ""},
		{"comma first valid", []string{"bad, " + hex}, hex},
		{"fallback", []string{"", colon}, hex},
		{"empty", []string{"", ""}, ""},
	}
	for _, tc := range cases {
		if got := linkCertificateFingerprint(tc.values...); got != tc.want {
			t.Errorf("%s: got %q want %q", tc.name, got, tc.want)
		}
	}
}

// 上游 vless.ts:348 原样保留 pcs，不校验格式，也不读 hpkp、不写 fingerprint。
func TestVLESSPinnedCertificate(t *testing.T) {
	colon := strings.TrimSuffix(strings.Repeat("AB:", 32), ":")
	cases := []struct {
		name  string
		query string
		want  any
	}{
		{"pcs kept", "&pcs=" + colon, colon},
		{"invalid pcs kept", "&pcs=nothex", "nothex"},
		{"hpkp ignored", "&hpkp=" + strings.Repeat("ab", 32), nil},
	}
	for _, tc := range cases {
		node, err := parseVLESSLink("vless://" + testUUID + "@example.com:443?security=tls&type=tcp" + tc.query + "#n")
		if err != nil {
			t.Fatalf("%s: %v", tc.name, err)
		}
		if got := node["pcs"]; got != tc.want {
			t.Errorf("%s: pcs %v want %v", tc.name, got, tc.want)
		}
		assertMapAbsent(t, node, "fingerprint")
	}
}

// SS 与上游一致：没有 # 时叫 "SS 节点"，# 后为空时给空名。
func TestLinkDefaultNames(t *testing.T) {
	ssUser := base64.RawURLEncoding.EncodeToString([]byte("aes-256-gcm:pass"))
	vmessJSON := base64.StdEncoding.EncodeToString([]byte(`{"v":"2","add":"example.com","port":443,"id":"` + testUUID + `","aid":0,"net":"tcp"}`))
	parse := map[string]func(string) (map[string]any, error){
		"vless":  parseVLESSLink,
		"trojan": parseTrojanLink,
		"ss":     parseSSLink,
		"vmess": func(raw string) (map[string]any, error) {
			node, _, err := parseVMessLink(raw)
			return node, err
		},
	}
	cases := []struct {
		kind, raw, want string
	}{
		// 上游 vless.ts:246：没有 fragment 和 remarks 时默认 "VLESS 节点"。
		{"vless", "vless://" + testUUID + "@example.com:443?security=tls&type=tcp", "VLESS 节点"},
		{"vless", "vless://" + testUUID + "@[2001:db8::1]:443?security=tls&type=tcp", "VLESS 节点"},
		{"vless", "vless://" + testUUID + "@example.com:443?security=tls&type=tcp&remarks=R", "R"},
		{"trojan", "trojan://pass@example.com:8443", "example.com:8443"},
		{"trojan", "trojan://pass@example.com:8443#Named", "Named"},
		{"ss", "ss://" + ssUser + "@example.com:8388", "SS 节点"},
		{"ss", "ss://" + ssUser + "@example.com:8388#My%20Node", "My Node"},
		{"ss", "ss://" + ssUser + "@example.com:8388#", ""},
		// 上游 vmess.ts：JSON 与 URI 格式默认 "VMess 节点"，std vmess 格式默认 host:port。
		{"vmess", "vmess://" + vmessJSON, "VMess 节点"},
		{"vmess", "vmess://" + testUUID + "@example.com:443?security=tls", "VMess 节点"},
		{"vmess", "vmess://tcp:" + testUUID + "-0@example.com:443/?", "example.com:443"},
	}
	for _, tc := range cases {
		node, err := parse[tc.kind](tc.raw)
		if err != nil {
			t.Fatalf("%s %s: %v", tc.kind, tc.raw, err)
		}
		if node["name"] != tc.want {
			t.Errorf("%s %s: name %v want %q", tc.kind, tc.raw, node["name"], tc.want)
		}
	}
}

func TestSSRDefaultName(t *testing.T) {
	b64 := func(s string) string { return base64.RawURLEncoding.EncodeToString([]byte(s)) }
	pass := b64("pass")
	cases := []struct {
		body, want string
	}{
		{"example.com:8388:origin:aes-256-cfb:plain:" + pass, "SSR-example.com:8388"},
		{"[2001:db8::1]:8388:origin:aes-256-cfb:plain:" + pass, "SSR-2001:db8::1:8388"},
		{"example.com:8388:origin:aes-256-cfb:plain:" + pass + "/?remarks=" + b64("R"), "R"},
	}
	for _, tc := range cases {
		node, err := parseSSRLink("ssr://" + b64(tc.body))
		if err != nil {
			t.Fatalf("%s: %v", tc.body, err)
		}
		if node["name"] != tc.want {
			t.Errorf("%s: name %v want %q", tc.body, node["name"], tc.want)
		}
	}
}
