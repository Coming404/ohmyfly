// 验证 YAML 特有规范化不重复改写字段，同时保持链接路径的既有语义。
package flight

import (
	"reflect"
	"testing"
)

func yamlNodeForTest(typ string) map[string]any {
	return map[string]any{"type": typ, "server": "test.example", "port": 443}
}

func TestNormalizeYAMLDoesNotRepeatTransportNormalization(t *testing.T) {
	input := yamlNodeForTest("vless")
	input["network"] = "ws"
	input["ws-opts"] = map[string]any{"path": "/?ed=2", "max-early-data": 1}
	input["sid"] = "abc"
	input["max-early-data"] = "12"
	yamlNode, err := NormalizeYAML(input)
	if err != nil {
		t.Fatal(err)
	}
	if getPath(yamlNode.Fields, "ws-opts", "path") != "/?ed=2" || getPath(yamlNode.Fields, "reality-opts", "short-id") != "abc" || yamlNode.Fields["max-early-data"] != "12" {
		t.Fatal("YAML transport was processed twice")
	}
	linkNode, err := Normalize(input)
	if err != nil {
		t.Fatal(err)
	}
	if getPath(linkNode.Fields, "ws-opts", "path") != "/" || getPath(linkNode.Fields, "reality-opts", "short-id") != "0abc" {
		t.Fatal("link semantics changed")
	}
	if input["sid"] != "abc" || input["ws-opts"].(map[string]any)["path"] != "/?ed=2" {
		t.Fatal("input mutated")
	}
}

func TestNormalizeYAMLEndpoints(t *testing.T) {
	cases := []struct {
		name  string
		node  map[string]any
		valid bool
	}{
		{"direct", map[string]any{"type": "direct"}, true},
		{"relay", map[string]any{"type": "relay"}, true},
		{"mieru", map[string]any{"type": "mieru", "server": "s.example", "port-range": "100-200"}, true},
		{"mieru_conflict", map[string]any{"type": "mieru", "server": "s.example", "port-range": "100-200", "port": 443}, false},
		{"mieru_reverse", map[string]any{"type": "mieru", "server": "s.example", "port-range": "200-100"}, false},
		{"peers_only", map[string]any{"type": "wireguard", "peers": []any{map[string]any{"server": "s.example", "port": 443, "public-key": "key"}}}, true},
		{"peers_empty", map[string]any{"type": "wireguard", "peers": []any{}}, false},
		{"peer_missing_key", map[string]any{"type": "wireguard", "peers": []any{map[string]any{"server": "s.example", "port": 443}}}, false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, err := NormalizeYAML(tc.node)
			if (err == nil) != tc.valid {
				t.Fatalf("valid=%v error=%v", tc.valid, err)
			}
		})
	}
}

func TestNormalizeYAMLProtocolAliases(t *testing.T) {
	node := yamlNodeForTest("wireguard")
	node["privateKey"], node["peerPublicKey"], node["psk"] = "private", "public", "shared"
	node["keepalive"], node["reserved"] = "25", "AQID"
	got, err := NormalizeYAML(node)
	if err != nil {
		t.Fatal(err)
	}
	if got.Fields["private-key"] != "private" || got.Fields["public-key"] != "public" || got.Fields["pre-shared-key"] != "shared" || got.Fields["persistent-keepalive"] != 25 {
		t.Fatal("aliases not folded")
	}
	if !reflect.DeepEqual(got.Fields["reserved"], []any{1, 2, 3}) {
		t.Fatal("reserved not normalized")
	}
	node["keepalive"] = 65536
	if _, err := NormalizeYAML(node); err == nil {
		t.Fatal("invalid keepalive accepted")
	}
}

func TestNormalizeYAMLEmptyAliasesAndSnell(t *testing.T) {
	node := yamlNodeForTest("vless")
	node["sid"] = ""
	got, err := NormalizeYAML(node)
	if err != nil {
		t.Fatal(err)
	}
	if _, exists := got.Fields["sid"]; !exists {
		t.Fatal("empty alias removed")
	}
	node = yamlNodeForTest("snell")
	node["shadow-tls-password"], node["shadow-tls-sni"] = "secret", "outer.example"
	node["obfs-opts"] = map[string]any{"mode": "shadow-tls", "host": "existing.example"}
	got, err = NormalizeYAML(node)
	if err != nil {
		t.Fatal(err)
	}
	if getPath(got.Fields, "obfs-opts", "host") != "existing.example" || getPath(got.Fields, "obfs-opts", "password") != "secret" {
		t.Fatal("shadow TLS precedence changed")
	}
	if _, exists := got.Fields["shadow-tls-password"]; exists {
		t.Fatal("old alias remains")
	}
}

func TestNormalizeYAMLCertificateLabels(t *testing.T) {
	for _, prefix := range []string{"", "sha256:", "SHA256=", "SHA256 Fingerprint = "} {
		t.Run(prefix, func(t *testing.T) {
			node := yamlNodeForTest("vless")
			hash := prefix + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
			node["fingerprint"], node["fp"] = hash, "chrome"
			got, err := NormalizeYAML(node)
			if err != nil {
				t.Fatal(err)
			}
			if got.Fields["fingerprint"] != hash || got.Fields["client-fingerprint"] != "chrome" {
				t.Fatal("certificate fingerprint confused with client fingerprint")
			}
		})
	}
}

func TestNormalizeYAMLKeepaliveNullPrecedence(t *testing.T) {
	node := yamlNodeForTest("wireguard")
	node["persistentKeepalive"] = nil
	if _, err := NormalizeYAML(node); err == nil {
		t.Fatal("explicit final null accepted")
	}
	node["keepalive"] = 0
	got, err := NormalizeYAML(node)
	if err != nil || got.Fields["persistent-keepalive"] != 0 {
		t.Fatal("earlier zero lost its priority")
	}
}
