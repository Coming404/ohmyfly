//go:build integration

// 验证 Mihomo 专用节点从目录存储导出后仍保留扁平字段。
package catalog

import (
	"context"
	"strings"
	"testing"

	"go.yaml.in/yaml/v3"
)

// TestExportMihomoNodesFlattensFieldsAndRestoresNames 验证节点配置展平及名称恢复。
func TestExportMihomoNodesFlattensFieldsAndRestoresNames(t *testing.T) {
	s := reviewStore(t)
	nodes := reviewParse(t, `proxies:
  - name: Mieru
    type: mieru
    server: mieru.example
    port-range: 2090-2099
    username: user
    password: secret
  - name: OpenVPN
    type: openvpn
    server: vpn.example
    port: 1194
    proto: udp
    ca: ca-data
  - name: Tailscale
    type: tailscale
    hostname: laptop
    auth-key: tskey-auth-example
    ephemeral: true
`)
	if len(nodes) != 3 {
		t.Fatalf("parsed %d nodes, want 3", len(nodes))
	}
	towerID := reviewImport(t, s, "local:text:export-protocols", nodes)
	var ids []int64
	rows, err := s.db.Query("SELECT id FROM nodes ORDER BY id")
	if err != nil {
		t.Fatal(err)
	}
	for rows.Next() {
		var id int64
		if err := rows.Scan(&id); err != nil {
			t.Fatal(err)
		}
		ids = append(ids, id)
	}
	if err := rows.Close(); err != nil {
		t.Fatal(err)
	}
	if len(ids) != len(nodes) {
		t.Fatalf("tower %d has %d stored nodes, want %d", towerID, len(ids), len(nodes))
	}
	text, err := s.ExportYAML(context.Background(), ids)
	if err != nil {
		t.Fatal(err)
	}
	var document struct {
		Proxies []map[string]any `yaml:"proxies"`
	}
	if err := yaml.Unmarshal([]byte(text), &document); err != nil {
		t.Fatal(err)
	}
	if len(document.Proxies) != 3 {
		t.Fatalf("exported %d nodes, want 3", len(document.Proxies))
	}
	for index, name := range []string{"Mieru", "OpenVPN", "Tailscale"} {
		proxy := document.Proxies[index]
		if proxy["name"] != name {
			t.Errorf("proxy %d name = %#v, want %q", index, proxy["name"], name)
		}
		if _, nested := proxy["fields"]; nested {
			t.Errorf("proxy %q contains internal fields wrapper", name)
		}
	}
	if document.Proxies[0]["port-range"] != "2090-2099" || document.Proxies[1]["ca"] != "ca-data" || document.Proxies[2]["hostname"] != "laptop" || document.Proxies[2]["auth-key"] != "tskey-auth-example" {
		t.Fatalf("Mihomo-specific fields lost during export: %s", strings.TrimSpace(text))
	}
	if _, hasServer := document.Proxies[2]["server"]; hasServer {
		t.Fatal("Tailscale export must not synthesize server")
	}
}
