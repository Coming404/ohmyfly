//go:build integration

package catalog

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"testing"
)

func TestLocalTowerFlights(t *testing.T) {
	dir := os.Getenv("REVIEW_DATA_DIR")
	if dir == "" {
		t.Skip("需提供外置真实订阅目录 REVIEW_DATA_DIR")
	}
	data, err := os.ReadFile(filepath.Join(dir, "source1.yaml"))
	if err != nil {
		t.Fatal(err)
	}
	s := reviewStore(t)
	ctx := context.Background()
	nodes := reviewParse(t, string(data))
	id := reviewImport(t, s, "local:file:review", nodes)
	reviewImport(t, s, "local:text:review", nodes)
	summary, err := s.Summary(ctx)
	if err != nil {
		t.Fatal(err)
	}
	towers, err := s.Page(ctx, "towers", 0, false, summary.Revision, 0)
	if err != nil || len(towers.Towers) != 2 || towers.Towers[0].SourceKind != "file" || towers.Towers[1].SourceKind != "text" {
		t.Fatalf("来源类型未保留: %v", err)
	}
	seen := map[int64]bool{}
	var anchor int64
	for {
		page, err := s.TowerFlights(ctx, id, anchor, summary.Revision)
		if err != nil {
			t.Fatal(err)
		}
		if page.MatchCount != towers.Towers[0].NodeCount || len(page.Flights) > 100 {
			t.Fatal("来源计数或批次大小不一致")
		}
		for _, node := range page.Flights {
			if node.ID <= anchor || seen[node.ID] || node.Measurements.NodeID != node.ID {
				t.Fatal("重复节点、游标逆序或摘要关联错误")
			}
			seen[node.ID] = true
		}
		if len(page.Flights) < 100 {
			break
		}
		anchor = page.Flights[len(page.Flights)-1].ID
	}
	if int64(len(seen)) != towers.Towers[0].NodeCount {
		t.Fatal("遍历遗漏来源节点")
	}
	if err = s.Remove(ctx, id); err != nil {
		t.Fatal(err)
	}
	if _, err = s.TowerFlights(ctx, id, 0, summary.Revision); !errors.Is(err, ErrStale) {
		t.Fatal("旧修订仍可发布来源节点")
	}
	current, err := s.Summary(ctx)
	if err != nil || current.Flights != summary.Flights {
		t.Fatal("删除文件来源损坏共享节点")
	}
}
