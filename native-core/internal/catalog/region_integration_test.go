//go:build integration

// 验证地区填充跨越导入、分页和旧库初始化，且不污染协议配置。
package catalog

import (
	"context"
	"fmt"
	"path/filepath"
	"strings"
	"testing"
)

// TestNodeRegionsImportAndPages 验证三种名称都从真实入库链得到中文摘要。
func TestNodeRegionsImportAndPages(t *testing.T) {
	ctx := context.Background()
	s := reviewStore(t)
	names := []string{"Hong Kong", "HK", "香港", "cloudflare", "cf", "未识别线路", "Tokyo", "狮城", "🇯🇵节点"}
	want := []string{"香港", "香港", "香港", "CloudFlare", "CloudFlare", "未知", "日本", "新加坡", "日本"}
	codes := []string{"HK", "HK", "HK", "cloudflare", "cloudflare", "", "JP", "SG", "JP"}
	nodes := make([]Node, len(names))
	for i, name := range names {
		nodes[i] = reviewParse(t, fmt.Sprintf("proxies: [{name: '%s', type: trojan, server: node%d.example, port: 443, password: fixture}]", name, i))[0]
	}
	tower := reviewImport(t, s, "regions", nodes)
	ticket := reviewFilter(t, s, func(FilterInput) bool { return true })
	page, err := s.Page(ctx, "flights", 0, false, ticket.Revision, ticket.Token)
	if err != nil {
		t.Fatal(err)
	}
	towerPage, err := s.TowerFlights(ctx, tower, 0, ticket.Revision)
	if err != nil {
		t.Fatal(err)
	}
	for _, list := range [][]FlightSummary{page.Flights, towerPage.Flights} {
		if len(list) != len(names) {
			t.Fatalf("got %d nodes", len(list))
		}
		for _, node := range list {
			index := -1
			for i, name := range names {
				if name == node.Name {
					index = i
					break
				}
			}
			if index < 0 || node.Region != want[index] {
				t.Fatalf("unexpected region: %+v", node)
			}
			if node.RegionCode != codes[index] {
				t.Fatalf("unexpected icon code: %+v", node)
			}
			var config string
			if err = s.db.QueryRowContext(ctx, "SELECT config FROM nodes WHERE id=?", node.ID).Scan(&config); err != nil {
				t.Fatal(err)
			}
			if strings.Contains(config, `"region"`) {
				t.Fatalf("region leaked into config: %s", config)
			}
		}
	}
}

// TestNodeRegionPriority 保留手动值，名称命中时覆盖展示，名称消失后恢复手动值。
func TestNodeRegionPriority(t *testing.T) {
	ctx := context.Background()
	s := reviewStore(t)
	tower := reviewImport(t, s, "priority", []Node{reviewNode(t, "HK")})
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer tx.Rollback()
	var id int64
	if err = tx.QueryRowContext(ctx, "SELECT id FROM nodes").Scan(&id); err != nil {
		t.Fatal(err)
	}
	if _, err = tx.ExecContext(ctx, "UPDATE node_regions SET manual_region='JP' WHERE node_id=?", id); err != nil {
		t.Fatal(err)
	}
	page := []FlightSummary{{ID: id}}
	if err = attachRegions(ctx, tx, page); err != nil {
		t.Fatal(err)
	}
	if page[0].Region != "香港" {
		t.Fatal(page)
	}
	if _, err = tx.ExecContext(ctx, "UPDATE nodes SET name='未识别线路' WHERE id=?", id); err != nil {
		t.Fatal(err)
	}
	if err = applyNameRegions(ctx, tx, tower); err != nil {
		t.Fatal(err)
	}
	page = []FlightSummary{{ID: id}}
	if err = attachRegions(ctx, tx, page); err != nil {
		t.Fatal(err)
	}
	if page[0].Region != "日本" {
		t.Fatal(page)
	}
}

// TestNodeRegionsExistingDatabase 模拟旧库未建立地区表，跨批补齐后重启仍保留地区。
func TestNodeRegionsExistingDatabase(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "regions.db")
	s, err := Open(ctx, path)
	if err != nil {
		t.Fatal(err)
	}
	defer func() { s.Close() }()
	nodes := make([]Node, 103)
	for i := range nodes {
		nodes[i] = reviewParse(t, fmt.Sprintf("proxies: [{name: HK%03d, type: trojan, server: node%d.example, port: 443, password: fixture}]", i, i))[0]
	}
	reviewImport(t, s, "existing", nodes)
	if _, err = s.db.ExecContext(ctx, "DROP TABLE node_regions; DROP TABLE region_catalog"); err != nil {
		t.Fatal(err)
	}
	if err = s.Close(); err != nil {
		t.Fatal(err)
	}
	s, err = Open(ctx, path)
	if err != nil {
		t.Fatal(err)
	}
	var count int
	if err = s.db.QueryRowContext(ctx, "SELECT count(*) FROM node_regions WHERE name_region='HK'").Scan(&count); err != nil {
		t.Fatal(err)
	}
	if count != len(nodes) {
		t.Fatalf("backfill got %d", count)
	}
	if err = s.Close(); err != nil {
		t.Fatal(err)
	}
	s, err = Open(ctx, path)
	if err != nil {
		t.Fatal(err)
	}
	if reviewCount(t, s, "node_regions") != len(nodes) {
		t.Fatal("restart lost regions")
	}
}
