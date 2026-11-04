// 移植自 SubBoost more-protocols.test.ts、protocol-edge-matrix.test.ts 与 trojan.ts 分支 @4015347（AGPL-3.0-only）。
// 这里的用例都是重写前本地实现与上游结果不同的输入。
package towerparse

import "testing"

func TestSubBoostTrojanRegressions(t *testing.T) {
	tests := []struct {
		name   string
		input  string
		want   map[string]any
		absent []string
	}{
		// mihomo 的 Trojan 适配器只读 sni（adapter/outbound/trojan.go:52），未给 sni 时用服务器地址。
		{name: "sni defaults to server", input: "trojan://secret@t.example.com:443", want: map[string]any{"sni": "t.example.com", "name": "Trojan 节点"}, absent: []string{"servername"}},
		{name: "peer as sni", input: "trojan://secret@t.example.com:443?peer=p.example.com", want: map[string]any{"sni": "p.example.com"}},
		// trojan.ts:33 只读 fragment，不读 remarks。
		{name: "remarks ignored", input: "trojan://secret@t.example.com:443?remarks=R", want: map[string]any{"name": "Trojan 节点"}},
		// trojan.ts:60 依次检查每个别名，前一个为假时继续看后一个；空值不算真。
		{name: "later insecure alias", input: "trojan://secret@t.example.com:443?allowInsecure=0&insecure=1", want: map[string]any{"skip-cert-verify": true}},
		{name: "empty insecure", input: "trojan://secret@t.example.com:443?allowInsecure=", absent: []string{"skip-cert-verify"}},
		// trojan.ts:45 只认 ws=1 或 ws=true。
		{name: "ws yes stays tcp", input: "trojan://secret@t.example.com:443?ws=yes", want: map[string]any{"network": "tcp"}, absent: []string{"ws-opts"}},
		// trojan.ts:104 host 直接写成 Host，不解析请求头串。
		{name: "host kept as Host", input: "trojan://secret@t.example.com:443?type=ws&host=Host:a.example.com|X:1", want: map[string]any{"ws-opts": map[string]any{"path": "/", "headers": map[string]any{"Host": "Host:a.example.com|X:1"}}}},
		// 上游忽略 pcs；这里写 mihomo 读取的 fingerprint，并保留原始 pcs 供导出。
		{name: "pcs as fingerprint", input: "trojan://secret@t.example.com:443?pcs=" + sha256Hex, want: map[string]any{"fingerprint": sha256Hex, "pcs": sha256Hex}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := parseTrojanLink(tt.input)
			if err != nil {
				t.Fatalf("parseTrojanLink(%q): %v", tt.input, err)
			}
			assertMapSubset(t, got, tt.want)
			assertMapAbsent(t, got, tt.absent...)
		})
	}
}

func TestSubBoostAnyTLSDefaultName(t *testing.T) {
	// anytls.ts:164，IPv6 不加方括号。
	for input, want := range map[string]string{
		"anytls://secret@a.example.com:443":   "AnyTLS-a.example.com:443",
		"anytls://secret@[2001:db8::1]:8443":  "AnyTLS-2001:db8::1:8443",
		"anytls://secret@a.example.com:443#F": "F",
	} {
		got, err := parseAnyTLSLink(input)
		if err != nil {
			t.Fatalf("parseAnyTLSLink(%q): %v", input, err)
		}
		if got["name"] != want {
			t.Errorf("parseAnyTLSLink(%q) name = %v, want %q", input, got["name"], want)
		}
	}
}

const sha256Hex = "abababababababababababababababababababababababababababababababab"
