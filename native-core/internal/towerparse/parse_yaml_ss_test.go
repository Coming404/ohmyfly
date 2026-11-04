// Ported from SubBoost packages/core/src/parser/clash-yaml.ts and clash-yaml.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import "testing"

// YAML 中的 ss/ssr 节点与链接解析走同一套插件规范化，并补上游默认值（clash-yaml.ts:314-337）。
func TestYAMLShadowsocksNormalization(t *testing.T) {
	input := `proxies:
  - {name: obfs, type: ss, server: s.example, port: 8388, password: pw, plugin: obfs-local, plugin-opts: {obfs: tls, obfs-host: cdn.example.com}}
  - {name: v2ray, type: ss, server: s.example, port: 8388, cipher: aes-128-gcm, password: pw, plugin: V2ray-Plugin, plugin-opts: {mode: websocket, tls: "true", mux: 0}}
  - {name: empty-plugin, type: ss, server: s.example, port: 8388, cipher: "", password: pw, plugin: "", plugin-opts: {mode: tls}}
  - {name: SSR Default, type: ssr, server: r.example, port: 8388, password: pw}
  - {name: ssr-kept, type: ssr, server: r.example, port: 8388, password: pw, cipher: chacha20, protocol: auth_aes128_md5, obfs: http_simple}
`
	nodes, _ := parseYAMLTest(t, input)
	byName := map[string]map[string]any{}
	for _, node := range nodes {
		byName[node.Name] = node.Fields
	}
	if len(byName) != 5 {
		t.Fatalf("got %d nodes, want 5", len(nodes))
	}
	assertMapSubset(t, byName["obfs"], map[string]any{
		"cipher": "aes-256-gcm", "plugin": "obfs",
		"plugin-opts": map[string]any{"mode": "tls", "host": "cdn.example.com"},
	})
	assertMapSubset(t, byName["v2ray"], map[string]any{
		"cipher": "aes-128-gcm", "plugin": "v2ray-plugin",
		"plugin-opts": map[string]any{"mode": "websocket", "tls": true, "mux": false},
	})
	assertMapSubset(t, byName["empty-plugin"], map[string]any{"cipher": "aes-256-gcm"})
	assertMapAbsent(t, byName["empty-plugin"], "plugin", "plugin-opts")
	// clash-yaml.test.ts:196.
	assertMapSubset(t, byName["SSR Default"], map[string]any{"cipher": "aes-256-cfb", "protocol": "origin", "obfs": "plain"})
	assertMapSubset(t, byName["ssr-kept"], map[string]any{"cipher": "chacha20", "protocol": "auth_aes128_md5", "obfs": "http_simple"})
}
