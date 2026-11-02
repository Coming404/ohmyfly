package towerparse

import "testing"

// Upstream keeps a password-like field only when it is a string or an integer
// a JS Number holds exactly; any other value skips the node.
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
	want := map[string]string{"a": "123", "b": "9007199254740991", "c": "1", "d": "0", "e": "007"}
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
	if stats.Skipped != 5 || stats.SkippedByReason["invalid YAML item"] != 5 {
		t.Fatalf("skipped = %d, reasons = %v", stats.Skipped, stats.SkippedByReason)
	}
}
