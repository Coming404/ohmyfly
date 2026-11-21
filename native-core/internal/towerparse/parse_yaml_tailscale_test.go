// Tailscale Mihomo YAML 允许没有传统 server/port 端点。
package towerparse

import (
	"testing"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

// 验证无服务器地址的 Tailscale 节点及凭据参与 ContentHash。
func TestTailscaleYAMLWithoutServerEndpoint(t *testing.T) {
	nodes, stats := parseYAMLTest(t, `proxies:
  - name: tail
    type: tailscale
    hostname: laptop
    auth-key: tskey-auth-example
    control-url: https://control.example
    ephemeral: true
    accept-routes: true
    exit-node: exit.example
    exit-node-allow-lan-access: true
`)
	if stats.Parsed != 1 || len(nodes) != 1 {
		t.Fatalf("Tailscale YAML not imported: stats=%+v", stats)
	}
	node := nodes[0]
	if node.Type != "tailscale" || node.Name != "tail" || node.Server != "" || node.Port != 0 {
		t.Fatalf("unexpected Tailscale endpoint: %+v", node)
	}
	for key, want := range map[string]any{
		"hostname": "laptop", "auth-key": "tskey-auth-example", "control-url": "https://control.example",
		"ephemeral": true, "accept-routes": true, "exit-node": "exit.example", "exit-node-allow-lan-access": true,
	} {
		if node.Fields[key] != want {
			t.Errorf("%s = %#v, want %#v", key, node.Fields[key], want)
		}
	}
	withoutAuth := node
	withoutAuth.Fields = make(map[string]any, len(node.Fields)-1)
	for key, value := range node.Fields {
		if key != "auth-key" {
			withoutAuth.Fields[key] = value
		}
	}
	if flight.ContentHash(node) == flight.ContentHash(withoutAuth) {
		t.Fatal("Tailscale credentials do not affect ContentHash")
	}
}
