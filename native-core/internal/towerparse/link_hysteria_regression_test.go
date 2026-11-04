// Hysteria、Hysteria2 与 SubBoost 4015347 的有意偏离：输出按 mihomo v1.19.33 适配器能读取的形式写。
package towerparse

import (
	"strings"
	"testing"
)

// mihomo 的 StringToBps 只认 "^\d+\s*[KMGT]?[Bb]ps$" 或纯数字；上游的 "50 mbps"、"1gbps" 会读成 0。
func TestHysteriaBandwidthReadableByMihomo(t *testing.T) {
	cases := []struct {
		name, input, up, down string
		parse                 func(string) (map[string]any, error)
	}{
		{"v2 digits get Mbps", "hy2://s@h.example.com?up=50&down=100", "50 Mbps", "100 Mbps", parseHysteria2Link},
		{"v2 lowercase units", "hy2://s@h.example.com?up=10kbps&down=1gbps", "10 Kbps", "1 Gbps", parseHysteria2Link},
		{"v2 short units", "hy2://s@h.example.com?up=5m&down=2%20g", "5 Mbps", "2 Gbps", parseHysteria2Link},
		{"v2 readable kept", "hy2://s@h.example.com?up=10%20MBps&down=20Mbps", "10 MBps", "20Mbps", parseHysteria2Link},
		{"v2 unknown kept", "hy2://s@h.example.com?up=fast&down=10x", "fast", "10x", parseHysteria2Link},
		{"v1 digits kept", "hysteria://h.example.com?upmbps=10&downmbps=%2020", "10", "20", parseHysteriaLink},
		{"v1 lowercase units", "hysteria://h.example.com?up=10mbps&down=1gbps", "10 Mbps", "1 Gbps", parseHysteriaLink},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			node, err := tc.parse(tc.input)
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, node, map[string]any{"up": tc.up, "down": tc.down})
		})
	}
}

// mihomo 的 fingerprint 只接受 SHA-256，fp=chrome 这类值会让 TLS 配置报错，节点无法加载。
func TestHysteria2CertificateFingerprint(t *testing.T) {
	sha := strings.Repeat("ab", 32)
	cases := []struct {
		name, query string
		want        any
	}{
		{"client fingerprint skipped", "fp=chrome&pinSHA256=" + sha, sha},
		{"colon upper normalized", "fingerprint=" + strings.TrimSuffix(strings.Repeat("AB:", 32), ":"), sha},
		{"invalid dropped", "fp=nothex", nil},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			node, err := parseHysteria2Link("hy2://s@h.example.com?" + tc.query)
			if err != nil {
				t.Fatal(err)
			}
			if got := node["fingerprint"]; got != tc.want {
				t.Errorf("fingerprint = %v, want %v", got, tc.want)
			}
		})
	}
}

// 默认名与上游一致：Hysteria 为 "Hysteria host:port"（IPv6 不加方括号），Hysteria2 为 "Hysteria2 节点"。
func TestSubBoostHysteriaDefaultNames(t *testing.T) {
	cases := []struct {
		name, input, want string
		parse             func(string) (map[string]any, error)
	}{
		{"v1", "hysteria://h.example.com", "Hysteria h.example.com:443", parseHysteriaLink},
		{"v1 IPv6", "hy://[2001:db8::1]:8443", "Hysteria 2001:db8::1:8443", parseHysteriaLink},
		{"v1 empty fragment", "hysteria://h.example.com#", "Hysteria h.example.com:443", parseHysteriaLink},
		{"v2", "hy2://s@h.example.com:8443", "Hysteria2 节点", parseHysteria2Link},
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
