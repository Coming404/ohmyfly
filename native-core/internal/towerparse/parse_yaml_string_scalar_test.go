// 验证 YAML 数字凭据保留原文，非整数数值和非标量仍被拒绝。
package towerparse

import "testing"

// 整数字面量先保留原文；浮点数只有安全整数才能转为字符串。
func TestYAMLStringScalarFields(t *testing.T) {
	input := `proxies:
  - {name: a, type: ss, server: s.example, port: 8388, cipher: aes-128-gcm, password: 0123}
  - {name: b, type: ss, server: s.example, port: 8388, cipher: aes-128-gcm, password: 9007199254740991}
  - {name: c, type: ss, server: s.example, port: 8388, cipher: aes-128-gcm, password: 1.0}
  - {name: d, type: ss, server: s.example, port: 8388, cipher: aes-128-gcm, password: -0}
  - {name: e, type: ss, server: s.example, port: 8388, cipher: aes-128-gcm, password: "007"}
  - {name: f, type: ss, server: s.example, port: 8388, cipher: aes-128-gcm, password: 9007199254740992}
  - {name: g, type: ss, server: s.example, port: 8388, cipher: aes-128-gcm, password: 99999999999999999999}
  - {name: h, type: ss, server: s.example, port: 8388, cipher: aes-128-gcm, password: 1.5}
  - {name: i, type: ss, server: s.example, port: 8388, cipher: aes-128-gcm, password: true}
  - {name: j, type: ss, server: s.example, port: 8388, cipher: aes-128-gcm, password: [1]}
`
	want := map[string]string{"a": "0123", "b": "9007199254740991", "c": "1", "d": "-0", "e": "007", "f": "9007199254740992", "g": "99999999999999999999"}
	nodes, stats := parseYAMLTest(t, input)
	if len(nodes) != len(want) {
		t.Fatalf("got %d nodes, want %d", len(nodes), len(want))
	}
	for _, node := range nodes {
		got, ok := node.Fields["password"].(string)
		if !ok || got != want[node.Name] {
			t.Errorf("%s: password = %#v, want %q", node.Name, node.Fields["password"], want[node.Name])
		}
	}
	if stats.Skipped != 3 || stats.SkippedByReason["invalid YAML item"] != 3 {
		t.Fatalf("skipped = %d, reasons = %v", stats.Skipped, stats.SkippedByReason)
	}
}
