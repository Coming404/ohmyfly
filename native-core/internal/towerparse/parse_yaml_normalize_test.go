// 对照 SubBoost clash-yaml.ts @4015347 的 YAML 公共入口回归。
package towerparse

import (
	"reflect"
	"strings"
	"testing"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

func TestYAMLNormalizeProtocolDefaults(t *testing.T) {
	tests := []struct {
		name, extra string
		want        map[string]any
	}{
		{"ss", "", map[string]any{"cipher": "aes-256-gcm", "password": ""}},
		{"ssr", "", map[string]any{"cipher": "aes-256-cfb", "password": "", "protocol": "origin", "obfs": "plain"}},
		{"vmess", ", alterId: '0x10'", map[string]any{"uuid": "", "cipher": "auto", "alterId": float64(16)}},
		{"vless", "", map[string]any{"uuid": ""}},
		{"trojan", "", map[string]any{"password": ""}},
		{"anytls", "", map[string]any{"password": ""}},
		{"hy2", "", map[string]any{"type": "hysteria2", "password": ""}},
		{"hy", "", map[string]any{"type": "hysteria", "protocol": "udp"}},
		{"socks", "", map[string]any{"type": "socks5"}},
		{"tuic", "", map[string]any{"uuid": "", "password": ""}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			nodes, stats := parseYAMLTest(t, "{type: "+tt.name+", server: test.example, port: 443"+tt.extra+"}")
			if len(nodes) != 1 || stats.Skipped != 0 {
				t.Fatalf("nodes=%v stats=%+v", nodes, stats)
			}
			if nodes[0].Name != "未命名节点" {
				t.Fatalf("name=%q", nodes[0].Name)
			}
			assertMapSubset(t, nodes[0].Fields, tt.want)
		})
	}
}

func TestYAMLNormalizePortsAndDefaults(t *testing.T) {
	input := `proxies:
  - {name: hy2, type: hy2, server: test.example, port: invalid, ports: "9000 / 440-445; 8443"}
  - {name: hy, type: hy, server: test.example, ports: 443}
  - {name: decimal, type: vmess, server: test.example, port: "0443", alterId: ["2.5"]}
  - {name: boolean-password, type: trojan, server: test.example, port: 443, password: false}
  - {name: zero-password, type: trojan, server: test.example, port: 443, password: 0}
  - {name: hex-port, type: ss, server: test.example, port: "0x1bb"}
`
	nodes, stats := parseYAMLTest(t, input)
	if len(nodes) != 3 || stats.Skipped != 3 {
		t.Fatalf("nodes=%v stats=%+v", nodes, stats)
	}
	assertMapSubset(t, nodes[0].Fields, map[string]any{"port": 443, "ports": "9000,440-445,8443", "password": ""})
	assertMapSubset(t, nodes[1].Fields, map[string]any{"port": 443, "alterId": float64(2.5)})
	assertMapSubset(t, nodes[2].Fields, map[string]any{"password": "0"})
}

func TestYAMLNormalizeNestedOptions(t *testing.T) {
	input := `proxies:
  - {type: vless, server: test.example, port: 443, reality-opts: {public-key: "  key  ", short-id: invalid, sid: abc, spider-x: /}, xhttp-opts: {path: /keep}}
  - {type: vless, server: test.example, port: 443, reality-opts: {short-id: "0x"}}
  - {type: vmess, server: test.example, port: 443, network: ws, ws-opts: {path: " /ws?ed=128 ", max-early-data: 64}}
  - {type: ss, server: test.example, port: 443, plugin: V2Ray-Plugin, plugin-opts: [one, two]}
  - {type: ss, server: test.example, port: 443, plugin: 1}
`
	nodes, stats := parseYAMLTest(t, input)
	if len(nodes) != 4 || stats.Skipped != 1 {
		t.Fatalf("nodes=%v stats=%+v", nodes, stats)
	}
	assertMapSubset(t, nodes[0].Fields, map[string]any{
		"reality-opts": map[string]any{"public-key": "key", "spider-x": "/"},
		"xhttp-opts":   map[string]any{"path": "/keep"},
	})
	assertMapAbsent(t, nodes[1].Fields, "reality-opts")
	if want := (map[string]any{"path": "/ws", "max-early-data": 64}); !reflect.DeepEqual(nodes[2].Fields["ws-opts"], want) {
		t.Fatalf("ws-opts=%v want=%v", nodes[2].Fields["ws-opts"], want)
	}
	assertMapSubset(t, nodes[3].Fields, map[string]any{"plugin": "v2ray-plugin", "plugin-opts": map[string]any{"0": "one", "1": "two"}})
}

func TestYAMLNormalizeScannerAndPublicEntry(t *testing.T) {
	item := "- {type: hy2, server: test.example, ports: '9000/443', opaque: {enabled: true}}\n"
	want, stats := parseYAMLTest(t, "proxies:\n"+item)
	if len(want) != 1 || stats.Skipped != 0 {
		t.Fatalf("nodes=%v stats=%+v", want, stats)
	}
	recovered, _ := scanYAMLTest(t, "proxies:\n"+item)
	if !reflect.DeepEqual(recovered, want) {
		t.Fatalf("scanner=%v document=%v", recovered, want)
	}
	for _, input := range []string{"proxies:\n" + item, item, strings.TrimPrefix(item, "- ")} {
		var got []flight.Flight
		_, err := Parse(strings.NewReader(input), func(node flight.Flight) error { got = append(got, node); return nil })
		if err != nil || !reflect.DeepEqual(got, want) {
			t.Fatalf("public entry=%v err=%v want=%v", got, err, want)
		}
	}
}

func TestYAMLNormalizeFilteringAndIdentity(t *testing.T) {
	input := `proxies:
  - null
  - false
  - 1
  - text
  - []
  - {name: implicit, type: hy2, server: test.example, ports: "9000 / 443", extra: {name: keep}}
  - {name: explicit, type: hysteria2, server: test.example, port: 443, ports: "9000,443", password: "", extra: {name: keep}}
`
	nodes, stats := parseYAMLTest(t, input)
	if len(nodes) != 2 || stats.Skipped != 1 || stats.Candidates != 3 {
		t.Fatalf("nodes=%v stats=%+v", nodes, stats)
	}
	if flight.Fingerprint(nodes[0]) != flight.Fingerprint(nodes[1]) {
		t.Fatal("协议别名、默认密码和跳跃端口应产生相同内容身份")
	}
	assertMapSubset(t, nodes[0].Fields, map[string]any{"extra": map[string]any{"name": "keep"}})
}

func TestYAMLNormalizeNumberCoercion(t *testing.T) {
	tests := []struct {
		name, value string
		want        float64
	}{
		{"true", "true", 1}, {"false", "false", 0}, {"null", "null", 0},
		{"empty-array", "[]", 0}, {"single-array", "['2.5']", 2.5},
		{"multi-array", "[1, 2]", 0}, {"object", "{}", 0},
		{"decimal", "'03'", 3}, {"hex", "'0x10'", 16},
		{"binary", "'0b11'", 3}, {"octal", "'0o17'", 15},
		{"signed-hex-invalid", "'-0x10'", 0}, {"large-hex", "'0xfffffffffffffffff'", 295147905179352830000},
		{"exponent", "'1e-3'", .001}, {"negative", "-2", -2},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			nodes, stats := parseYAMLTest(t, "{type: vmess, server: test.example, port: 443, alterId: "+tt.value+"}")
			if len(nodes) != 1 || stats.Skipped != 0 || nodes[0].Fields["alterId"] != tt.want {
				t.Fatalf("nodes=%v stats=%+v want alterId=%v", nodes, stats, tt.want)
			}
		})
	}
}

func TestYAMLNormalizePassThroughBranches(t *testing.T) {
	for _, typ := range []string{"socks4", "socks5", "http", "https", "ssh", "mieru", "masque", "sudoku", "wireguard", "trusttunnel", "new-protocol"} {
		t.Run(typ, func(t *testing.T) {
			input := "{type: " + typ + ", server: test.example, port: 443, username: user, password: pw, opaque: {key: value}, ws-opts: [keep], tls: false}"
			nodes, stats := parseYAMLTest(t, input)
			if len(nodes) != 1 || stats.Skipped != 0 {
				t.Fatalf("nodes=%v stats=%+v", nodes, stats)
			}
			assertMapSubset(t, nodes[0].Fields, map[string]any{
				"type": typ, "opaque": map[string]any{"key": "value"},
				"ws-opts": []any{"keep"}, "tls": false,
			})
		})
	}
}

func TestYAMLNormalizeObjectConversions(t *testing.T) {
	input := `proxies:
  - {type: {toString: 1}, server: test.example, port: 443}
  - {type: vmess, server: test.example, port: 443, alterId: [{toString: null}]}
  - {type: vmess, server: test.example, port: 443, network: {toString: 1}}
  - {type: [ss], server: test.example, port: 443}
  - {type: vmess, server: test.example, port: 443, network: [ws], ws-opts: {path: "/ws?ed=128"}}
`
	nodes, stats := parseYAMLTest(t, input)
	if len(nodes) != 2 || stats.Skipped != 3 {
		t.Fatalf("nodes=%v stats=%+v", nodes, stats)
	}
	assertMapSubset(t, nodes[0].Fields, map[string]any{"type": "ss", "password": ""})
	assertMapSubset(t, nodes[1].Fields, map[string]any{"ws-opts": map[string]any{"path": "/ws", "max-early-data": 128}})
}

func TestYAMLNormalizeRealityPrefixOrder(t *testing.T) {
	for _, id := range []string{"0 xab", "0x0Xab", "0\uFEFFxab"} {
		t.Run(id, func(t *testing.T) {
			nodes, stats := parseYAMLTest(t, "{type: vless, server: test.example, port: 443, reality-opts: {short-id: '"+id+"'}}")
			if len(nodes) != 1 || stats.Skipped != 0 {
				t.Fatalf("nodes=%v stats=%+v", nodes, stats)
			}
			assertMapAbsent(t, nodes[0].Fields, "reality-opts")
		})
	}
}
