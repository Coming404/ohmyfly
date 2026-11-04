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

// fingerprint 写第一个有效 SHA-256，供 mihomo 校验证书；pcs 原样保留，供导出分享链接。
// 上游 VLESS、AnyTLS 只保留 pcs，Trojan 两者都不写，mihomo 适配器只读 fingerprint。
func TestLinkPinnedCertificate(t *testing.T) {
	hex := strings.Repeat("ab", 32)
	colon := strings.TrimSuffix(strings.Repeat("AB:", 32), ":")
	parsers := map[string]func(string) (map[string]any, error){
		"vless://" + testUUID + "@example.com:443?security=tls&type=tcp": parseVLESSLink,
		"trojan://secret@example.com:443?type=tcp":                       parseTrojanLink,
		"anytls://secret@example.com:443?sni=example.com":                parseAnyTLSLink,
	}
	cases := []struct {
		name             string
		query            string
		fingerprint, pcs any
	}{
		{"pcs", "&pcs=" + colon, hex, colon},
		{"multiple pcs", "&pcs=bad," + hex + "," + strings.Repeat("cd", 32), hex, "bad," + hex + "," + strings.Repeat("cd", 32)},
		{"hpkp", "&hpkp=" + hex, hex, nil},
		{"pcs wins", "&pcs=" + hex + "&hpkp=" + strings.Repeat("cd", 32), hex, hex},
		{"invalid", "&pcs=nothex", nil, "nothex"},
	}
	for base, parse := range parsers {
		for _, tc := range cases {
			node, err := parse(base + tc.query + "#n")
			if err != nil {
				t.Fatalf("%s %s: %v", base, tc.name, err)
			}
			if got := node["fingerprint"]; got != tc.fingerprint {
				t.Errorf("%s %s: fingerprint %v want %v", base, tc.name, got, tc.fingerprint)
			}
			if got := node["pcs"]; got != tc.pcs {
				t.Errorf("%s %s: pcs %v want %v", base, tc.name, got, tc.pcs)
			}
		}
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
		// 上游 trojan.ts:33：只看 fragment，默认 "Trojan 节点"，不读 remarks。
		{"trojan", "trojan://pass@example.com:8443", "Trojan 节点"},
		{"trojan", "trojan://pass@example.com:8443?remarks=R", "Trojan 节点"},
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
